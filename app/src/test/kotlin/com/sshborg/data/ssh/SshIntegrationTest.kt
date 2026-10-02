package com.sshborg.data.ssh

import com.jcraft.jsch.JSchException
import com.sshborg.TestConfig
import java.security.Security
import kotlinx.coroutines.runBlocking
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * The SSH layer against a real server, which is the only place some of this can be checked at
 * all: whether a key actually authenticates is a fact about a server, not about our code.
 *
 * Skipped unless `test.properties` names a server and a directory of throwaway keys authorized
 * on it — the machine the tests run on is the simplest choice. See `test.properties.example`.
 */
class SshIntegrationTest {

    private val configured = TestConfig.sshServer()

    private fun server(): TestConfig.Server {
        assumeTrue(TestConfig.NO_SERVER, configured != null)
        return configured!!
    }

    private fun connect(auth: SshAuth, throughItself: Boolean = false): ShellSession {
        val s = server()
        val params = SshConnectionParams(
            hostname = s.host,
            port = s.port,
            username = s.user,
            auth = auth,
            jumpHosts = if (!throughItself) emptyList() else listOf(
                JumpHost(host = s.host, port = s.port, username = s.user, knownHostsEntry = null, auth = auth),
            ),
        )
        return runBlocking { SshManager.openShell(params) { _, _, _ -> true } }
    }

    /**
     * Asks the shell to say something and waits until it does. The word is put together by the
     * shell itself, so the echo of the command cannot be mistaken for its output.
     */
    private fun ShellSession.assertAlive() {
        write("printf 'sshborg%s\\n' '-alive'\n".toByteArray())
        val deadline = System.currentTimeMillis() + 20_000
        val seen = StringBuilder()
        val buffer = ByteArray(4096)
        while (System.currentTimeMillis() < deadline) {
            if (inputStream.available() > 0) {
                val read = inputStream.read(buffer)
                if (read < 0) break
                seen.append(String(buffer, 0, read))
                if (seen.contains("sshborg-alive")) return
            } else {
                Thread.sleep(25)
            }
        }
        fail("the shell never answered; what came back was:\n$seen")
    }

    /**
     * Issue #18 from end to end, per algorithm: an encrypted key is imported the way the app
     * imports it, and what the app would have stored is then used to log in. Until 1.18.0 this
     * could not work for anyone — the key was stored still encrypted and the passphrase thrown
     * away — and nothing in the app noticed, which is why this test exists at all.
     */
    private fun unlockAndLogIn(name: String) {
        val s = server()
        val imported = SshManager.importPrivateKey(s.key(name), s.keyPassphrase)
        assertFalse("what we store must not be encrypted", SshManager.isKeyEncrypted(imported.pem))
        val session = connect(SshAuth.PublicKey(imported.pem))
        try {
            session.assertAlive()
        } finally {
            session.disconnect()
        }
    }

    @Test fun `an encrypted ed25519 key logs in after import`() = unlockAndLogIn("i_ed25519")
    @Test fun `an encrypted rsa key logs in after import`() = unlockAndLogIn("i_rsa")
    @Test fun `an encrypted ecdsa key logs in after import`() = unlockAndLogIn("i_ecdsa")

    @Test fun `an unencrypted key logs in`() {
        val session = connect(SshAuth.PublicKey(server().key("i_plain")))
        try {
            session.assertAlive()
        } finally {
            session.disconnect()
        }
    }

    /**
     * What makes the tests above mean anything: a key the server does not know must be turned
     * away. Uses the key corpus, whose keys are deliberately in no authorized_keys anywhere.
     */
    @Test fun `a key the server does not know is refused`() {
        val corpus = TestConfig.directory("KEY_CORPUS")
        assumeTrue(TestConfig.NO_KEY_CORPUS, corpus != null)
        val stranger = java.io.File(corpus, "k_plain")
        assumeTrue("k_plain is missing from the key corpus", stranger.isFile)
        try {
            connect(SshAuth.PublicKey(stranger.readText())).disconnect()
            fail("a key that is in no authorized_keys must not get in")
        } catch (e: JSchException) {
            assertTrue("refused, but not for the right reason: ${e.message}",
                e.message?.contains("Auth fail", ignoreCase = true) == true ||
                e.message?.contains("auth", ignoreCase = true) == true)
        }
    }

    /**
     * A jump chain, with the server as its own hop. Worth the oddity: the chain is where a lost
     * banner once deadlocked the connection for good — the target's greeting arrived before
     * anything was reading the channel and was dropped silently.
     */
    @Test fun `a jump chain reaches the target`() {
        val session = connect(SshAuth.PublicKey(server().key("i_plain")), throughItself = true)
        try {
            session.assertAlive()
            assertTrue("a hop with no stored key should hand one back",
                session.newJumpHostKeyLines.isNotEmpty())
        } finally {
            session.disconnect()
        }
    }

    /** A host key we hand back has to be accepted next time without asking the user again. */
    @Test fun `the known hosts line it produces is good enough for the next connect`() {
        val s = server()
        val auth = SshAuth.PublicKey(s.key("i_plain"))
        val first = connect(auth)
        val line = first.hostKeyLine
        first.disconnect()
        assertTrue("a known-hosts line should name the host", line.contains(s.host))

        var asked = false
        val params = SshConnectionParams(
            hostname = s.host, port = s.port, username = s.user, auth = auth, knownHostsEntry = line,
        )
        val second = runBlocking { SshManager.openShell(params) { _, _, _ -> asked = true; true } }
        try {
            second.assertAlive()
        } finally {
            second.disconnect()
        }
        assertFalse("a stored host key must not be asked about again", asked)
    }

    companion object {
        /**
         * Android ships an old BouncyCastle, so the app replaces the provider at startup and the
         * SSH layer counts on it being there. Mirrors SshBorgApp.onCreate.
         */
        @BeforeClass @JvmStatic fun useOurBouncyCastle() {
            Security.removeProvider("BC")
            Security.addProvider(BouncyCastleProvider())
        }
    }
}
