package com.sshborg.data.ssh

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shape of a key this app generates — the width its body wraps at, and that it can be read
 * back.
 *
 * Instrumented rather than a JVM test, and that is the whole point. On the oldest versions
 * supported here `java.util.Base64` is not the platform's: Android has it only from API 26, and
 * below that D8 rewrites the call to the desugared backport. A unit test on the host JVM would
 * exercise the JDK's implementation and prove nothing about the one that actually runs on a
 * phone. This runs wherever the suite runs, so on an API 24 emulator it checks the backport.
 *
 * The width matters because [SshManager] writes this PEM itself: 70, which is what ssh-keygen
 * emits for OpenSSH's own private-key format, and what the iOS app writes too. RSA and ECDSA keys
 * are not checked here — JSch writes those, in the older PEM format, where 64 is the convention
 * and the width is not ours to choose.
 */
@RunWith(AndroidJUnit4::class)
class KeyFormatTest {

    private val header = "-----BEGIN OPENSSH PRIVATE KEY-----"
    private val footer = "-----END OPENSSH PRIVATE KEY-----"

    private fun generate(comment: String = "test") =
        runBlocking { SshManager.generateKeyPair(type = "ed25519", comment = comment) }

    /** The lines between the header and the footer: the base64 body and nothing else. */
    private fun bodyOf(pem: String): List<String> {
        val lines = pem.trim().lines()
        assertEquals("the header is not what was expected", header, lines.first())
        assertEquals("the footer is not what was expected", footer, lines.last())
        return lines.subList(1, lines.size - 1)
    }

    @Test fun anEd25519KeyWrapsItsBodyAtSeventy() {
        val body = bodyOf(generate().first)
        assertTrue("a key with no body", body.isNotEmpty())
        // Every line full but the last, which is whatever is left over.
        body.dropLast(1).forEachIndexed { i, line ->
            assertEquals("line ${i + 1} is not 70 characters", 70, line.length)
        }
        assertTrue(
            "the last line is ${body.last().length} characters, which is more than a full one",
            body.last().length in 1..70,
        )
    }

    @Test fun theBodyIsBase64AndNothingElse() {
        // A width is only worth checking if what is being wrapped is right in the first place.
        val allowed = Regex("^[A-Za-z0-9+/=]+$")
        bodyOf(generate().first).forEach {
            assertTrue("\"$it\" is not base64", allowed.matches(it))
        }
    }

    @Test fun thePublicKeyIsTheOneLineThatAuthorizedKeysExpects() {
        val (_, pub) = generate(comment = "sshborg-test")
        assertEquals("a public key must be a single line", 1, pub.trim().lines().size)
        assertTrue("does not start with its algorithm: $pub", pub.startsWith("ssh-ed25519 "))
        assertTrue("the comment was dropped: $pub", pub.trim().endsWith(" sshborg-test"))
    }

    @Test fun whatIsWrittenCanBeReadBackAndStillMatchesItsPublicKey() {
        // The assertion that makes the others worth having: a body wrapped at a width nothing can
        // parse would still pass a length check. This one fails if the bytes are wrong.
        val (pem, pub) = generate(comment = "round-trip")
        val imported = SshManager.importPrivateKey(pem)
        assertEquals("ed25519", imported.keyType)
        assertEquals(
            "the key read back derives a different public key than the one generated with it",
            pub.trim().split(" ").take(2),
            imported.publicKey.trim().split(" ").take(2),
        )
    }

    @Test fun twoKeysGeneratedInARowAreNotTheSameKey() {
        // Cheap, and it would have caught a seeded or reused generator.
        assertTrue(generate().first != generate().first)
    }
}
