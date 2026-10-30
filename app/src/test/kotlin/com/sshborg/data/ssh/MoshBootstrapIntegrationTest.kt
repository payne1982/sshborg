package com.sshborg.data.ssh

import com.sshborg.TestConfig
import com.sshborg.mosh.MoshLabSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The gate on the handover: from SSH to UDP, end to end, with no Android in it.
 *
 * This is the part of the mosh integration that can be checked properly without a device, and it
 * is the part most likely to be wrong in a way reading cannot show: whether `mosh-server` actually
 * starts the way we ask it to, through the account's own login shell, and whether the line it
 * prints gets us a working session. `scripts/mosh-lab.sh` proves the protocol against a server
 * started by hand; this proves the *starting*.
 *
 * Skipped unless `test.properties` names a server and keys authorized on it, and the test is
 * skipped rather than failed when that server simply has no `mosh-server` — that is a fact about
 * the machine, not about us.
 */
class MoshBootstrapIntegrationTest {

    private val configured = TestConfig.sshServer()

    private fun login(): ShellSession {
        assumeTrue(TestConfig.NO_SERVER, configured != null)
        val s = configured!!
        val params = SshConnectionParams(
            hostname = s.host,
            port = s.port,
            username = s.user,
            auth = SshAuth.PublicKey(s.key("i_plain")),
        )
        return runBlocking { SshManager.openShell(params) { _, _, _, _ -> true } }
    }

    @Test fun `a chosen address family is dialled, and the host key still vouches by name`() {
        assumeTrue(TestConfig.NO_SERVER, configured != null)
        val s = configured!!
        // Deliberately by name and not by the configured address, because a name is the only
        // thing a family can be chosen *from* — and `localhost` is the one name every machine
        // has on both families, so this needs nothing of the configuration but the login.
        val both = runCatching { java.net.InetAddress.getAllByName(NAME) }.getOrNull().orEmpty()
        assumeTrue(
            "$NAME does not resolve to both families on this machine",
            both.any { it is java.net.Inet4Address } && both.any { it is java.net.Inet6Address },
        )

        val ssh = runBlocking {
            SshManager.openShell(
                SshConnectionParams(
                    hostname = NAME,
                    port = s.port,
                    username = s.user,
                    auth = SshAuth.PublicKey(s.key("i_plain")),
                    addressFamily = "ipv4",
                ),
            ) { _, _, _, _ -> true }
        }
        try {
            assertTrue("the session should be up", ssh.isConnected)
            assertEquals(
                "mosh must be handed the address SSH actually dialled, not the name to resolve again",
                "127.0.0.1", ssh.dialledAddress,
            )
            // The address it was dialled at is what mosh will be handed; the host key line is
            // still written under the *name*, which is the thing the pin is kept against. Getting
            // that second half wrong would turn a routing preference into a question about
            // trusting the server all over again.
            assertTrue(
                "the host key should still vouch for the name, not the address it was dialled at",
                ssh.hostKeyLine.startsWith("$NAME "),
            )
        } finally {
            runCatching { ssh.disconnect() }
        }
    }

    private companion object {
        /** The one name every machine has on both address families. */
        const val NAME = "localhost"
    }

    @Test fun `SSH starts a mosh-server and the session it hands over works`() {
        val ssh = login()
        var mosh: MoshLabSession? = null
        try {
            val handover = try {
                ssh.startMoshServer(networkTimeoutSeconds = 120)
            } catch (e: MoshUnavailable) {
                assumeTrue(
                    "the test server has no usable mosh-server (${e.reason})",
                    e.reason != MoshUnavailable.Reason.NotInstalled &&
                        e.reason != MoshUnavailable.Reason.LocaleNotUtf8,
                )
                throw e
            }
            assertTrue("the port is not in mosh's range: ${handover.port}", handover.port in 60001..60999)

            val session = MoshLabSession(
                TestConfig.Mosh(handover.host, handover.port, handover.key)
            ).also { mosh = it }

            session.resize(100, 30)
            session.type("printf 'bootstrap%s\\n' '-ok'\r")
            assertTrue(
                "the handed-over session never answered; UDP ${handover.port} may be blocked",
                session.awaitOutput("bootstrap-ok", 20_000),
            )

            // The SSH session is the whole point of doing it this way: the stock client closes it
            // here and gives up SFTP, port forwarding and the agent with it.
            assertTrue("the SSH session should still be up alongside mosh", ssh.isConnected)
            val sftp = ssh.openSftpChannel()
            try {
                assertTrue("SFTP on the surviving SSH session failed", sftp.home.isNotEmpty())
            } finally {
                runCatching { sftp.disconnect() }
            }

            assertTrue("the closing handshake went unanswered", session.shutdown(5_000))
        } finally {
            runCatching { mosh?.close() }
            runCatching { ssh.disconnect() }
        }
    }

    @Test fun `a server binary that is not there is reported as not installed`() {
        val ssh = login()
        try {
            ssh.startMoshServer(networkTimeoutSeconds = 60, command = "mosh-server-that-is-not-here")
            fail("a missing binary should not have produced a handover")
        } catch (e: MoshUnavailable) {
            assertEquals(MoshUnavailable.Reason.NotInstalled, e.reason)
        } finally {
            runCatching { ssh.disconnect() }
        }
    }
}
