package com.sshborg

import java.io.File

/**
 * What a test needs from the machine it is running on: where the corpora of real files are, and
 * a server it may talk to. It arrives as environment variables, which the build fills in from
 * `test.properties` — never committed, see `test.properties.example`.
 *
 * Nothing here has a default. A test whose settings are missing has to skip itself, not quietly
 * check something else: a green suite that tested nothing is worse than a skipped one.
 */
internal object TestConfig {

    fun value(name: String): String? = System.getenv("SSHBORG_$name")?.takeIf { it.isNotBlank() }

    fun directory(name: String): File? = value(name)?.let(::File)?.takeIf { it.isDirectory }

    /** A server the integration tests may open sessions against, or null if none is configured. */
    fun sshServer(): Server? {
        val host = value("SSH_HOST") ?: return null
        val user = value("SSH_USER") ?: return null
        val keys = directory("SSH_KEY_DIR") ?: return null
        val passphrase = value("SSH_KEY_PASSPHRASE") ?: return null
        return Server(host, value("SSH_PORT")?.toIntOrNull() ?: 22, user, keys, passphrase)
    }

    /**
     * @param keys directory of throwaway keys **authorized on this server**, unlike the ones in
     *   the key corpus, which are in no authorized_keys anywhere.
     */
    data class Server(
        val host: String,
        val port: Int,
        val user: String,
        val keys: File,
        val keyPassphrase: String,
    ) {
        fun key(name: String): String = File(keys, name).readText()
    }

    /**
     * A `mosh-server` the transport tests may hold a session with. Its key is minted per session
     * and lives only as long as the server does, so it arrives from `scripts/mosh-lab.sh` rather
     * than from `test.properties`: there is nothing stable to put in a file.
     */
    fun moshServer(): Mosh? {
        val port = value("MOSH_PORT")?.toIntOrNull() ?: return null
        val key = value("MOSH_KEY") ?: return null
        return Mosh(value("MOSH_HOST") ?: "127.0.0.1", port, key)
    }

    data class Mosh(val host: String, val port: Int, val key: String)

    const val NO_SERVER = "no server configured in test.properties (sshHost, sshUser, sshKeyDir)"
    const val NO_KEY_CORPUS = "keyCorpus is not set in test.properties"
    const val NO_EDITOR_CORPUS = "editorCorpus is not set in test.properties"
    const val NO_MOSH_SERVER = "no mosh-server to talk to; start one with scripts/mosh-lab.sh"
}
