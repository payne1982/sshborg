package com.sshborg.mosh

/**
 * The captured material the protocol tests run against: see `app/src/test/resources/mosh/`.
 *
 * [SESSION_KEY] is the key of the session those bytes came from — a `mosh-server` started by hand
 * on the development machine, bound to loopback, which exited the same afternoon. Mosh mints a
 * fresh key per session and prints it on its `MOSH CONNECT` line, so this one opens nothing: it is
 * here for the same reason a published test vector carries its key, because without it the
 * captured datagram cannot be checked against mosh's own ciphertext.
 */
object MoshVectors {

    const val SESSION_KEY = "qbtZl6/aO6DgGPP3f8IJcA"

    val serverDatagram: ByteArray get() = resource("server-datagram.bin")
    val serverPlaintext: ByteArray get() = resource("server-plaintext.bin")
    val hostDiff: ByteArray get() = resource("host-diff.bin")

    private fun resource(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/mosh/$name")) { "missing test vector $name" }
            .use { it.readBytes() }
}
