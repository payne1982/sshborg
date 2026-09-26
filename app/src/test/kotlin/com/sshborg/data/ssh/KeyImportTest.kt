package com.sshborg.data.ssh

import com.jcraft.jsch.JSchException
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Issue #18, kept fixed: an imported key protected by a passphrase is unlocked once, at import,
 * and stored unlocked — no passphrase is kept anywhere. Before the fix none of these keys could
 * ever authenticate, and nothing in the app noticed.
 *
 * This is the same thing that was proved with throwaway Java programs against the real jars, now
 * a test. It runs against the corpus of keys written by a real ssh-keygen, because the formats
 * are the whole point and JSch cannot write most of them itself. Throwaway keys that live outside
 * the repo and are in no authorized_keys:
 *
 *     ./gradlew test -PkeyCorpus=/path/to/key-test
 */
class KeyImportTest {

    private val corpus: File? =
        System.getenv("SSHBORG_KEY_CORPUS")?.let(::File)?.takeIf { it.isDirectory }

    private fun key(name: String): String {
        assumeTrue("SSHBORG_KEY_CORPUS is not set", corpus != null)
        val file = File(corpus, name)
        assumeTrue("$name is missing from the corpus", file.isFile)
        return file.readText()
    }

    /** The type and base64 of a public key, without the comment, which is ours to set. */
    private fun publicHalf(line: String) = line.trim().split(" ").take(2).joinToString(" ")

    private fun checkUnlocks(name: String, passphrase: String, expectedType: String) {
        val pem = key(name)
        assertTrue("$name should arrive encrypted", SshManager.isKeyEncrypted(pem))

        val imported = SshManager.importPrivateKey(pem, passphrase)

        assertEquals("$name: wrong key type", expectedType, imported.keyType)
        assertFalse("$name: what we store must not be encrypted", SshManager.isKeyEncrypted(imported.pem))
        assertEquals(
            "$name: the stored key is not the same key",
            publicHalf(File(corpus, "$name.pub").readText()),
            publicHalf(imported.publicKey),
        )
        // And the stored text has to be usable on its own, with no passphrase at all.
        val again = SshManager.importPrivateKey(imported.pem)
        assertEquals(publicHalf(imported.publicKey), publicHalf(again.publicKey))
    }

    @Test fun `ed25519 unlocks`() = checkUnlocks("k_ed25519", PASSPHRASE, "ed25519")
    @Test fun `rsa unlocks`() = checkUnlocks("k_rsa", PASSPHRASE, "rsa")
    @Test fun `ecdsa unlocks`() = checkUnlocks("k_ecdsa", PASSPHRASE, "ecdsa")
    @Test fun `old pem unlocks`() = checkUnlocks("k_rsa_pem", PASSPHRASE, "rsa")

    /** Accents and an emoji in the passphrase must reach JSch intact. */
    @Test fun `a non ascii passphrase works`() = checkUnlocks("k_utf8", "pässwörd☕ 1", "ed25519")

    @Test fun `an unencrypted key is stored exactly as it arrived`() {
        val pem = key("k_plain")
        assertFalse(SshManager.isKeyEncrypted(pem))
        val imported = SshManager.importPrivateKey(pem)
        assertEquals(pem.replace("\r\n", "\n").trim(), imported.pem)
        // A passphrase typed for a key that has none is ignored, not applied.
        assertEquals(imported.pem, SshManager.importPrivateKey(pem, "not needed").pem)
    }

    @Test fun `no passphrase for an encrypted key says so`() =
        assertFails("encrypted") { SshManager.importPrivateKey(key("k_ed25519")) }

    @Test fun `a wrong passphrase says so and stores nothing`() =
        assertFails("wrong_passphrase") { SshManager.importPrivateKey(key("k_ed25519"), "not it") }

    /**
     * A key JSch can read and cannot rewrite is refused outright, even with the right passphrase.
     * Refusing is the design: the alternative was keeping the passphrase for this one format,
     * which is a rarely taken branch holding a secret.
     */
    @Test fun `a pkcs8 container is refused`() =
        assertFails("unsupported_encryption") { SshManager.importPrivateKey(key("k_pkcs8"), PASSPHRASE) }

    private fun assertFails(reason: String, block: () -> Unit) {
        try {
            block()
            fail("expected to fail with \"$reason\"")
        } catch (e: JSchException) {
            assertEquals(reason, e.message)
        }
    }

    private companion object {
        /** The corpus README's passphrase for the keys that are not the odd one out. */
        const val PASSPHRASE = "segreto frase"
    }
}
