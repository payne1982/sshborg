package com.sshborg.data.ssh

/** A single jump host in a ProxyJump chain. */
data class JumpHost(
    val host: String,
    val port: Int,
    /** If null, the target host's username is used for this hop. */
    val username: String?,
    /** Known-hosts line for this jump host — null on first connect, non-null on subsequent connects. */
    val knownHostsEntry: String?,
)

/** All parameters needed to open an SSH connection. */
data class SshConnectionParams(
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val auth: SshAuth,
    val agentForwarding: Boolean = false,
    /** If null, fingerprint verification is skipped (only on first connect) */
    val knownHostsEntry: String? = null,
    /** Ordered list of SSH jump hosts to tunnel through before reaching the target. */
    val jumpHosts: List<JumpHost> = emptyList(),
)

sealed interface SshAuth {
    data class Password(val password: String) : SshAuth
    data class PublicKey(val privateKeyPem: String, val passphrase: String? = null) : SshAuth
}

/**
 * Parses a jump-hosts string ("user@host1:22,host2:port") and a newline-delimited known_hosts
 * blob into a list of [JumpHost].
 *
 * Each token format: [user@]host[:port]  — user and port are optional.
 */
fun parseJumpHosts(raw: String?, knownKeysBlob: String?): List<JumpHost> {
    if (raw.isNullOrBlank()) return emptyList()
    // Build a map: "host:port" -> known_hosts line.
    // JSch writes port-22 entries as "hostname ..." and non-22 entries as "[hostname]:port ...".
    // Normalise both to "host:port" so the lookup below always works regardless of port.
    val keysByHost: Map<String, String> = knownKeysBlob
        ?.lines()
        ?.filter { it.isNotBlank() }
        ?.mapNotNull { line ->
            val marker = line.substringBefore(" ")
            val canonical = if (marker.startsWith("[")) {
                // [host]:port format
                val h = marker.substringAfter("[").substringBefore("]")
                val p = marker.substringAfterLast("]:").toIntOrNull() ?: 22
                "$h:$p"
            } else {
                // bare hostname → port 22
                "$marker:22"
            }
            canonical to line
        }
        ?.toMap()
        ?: emptyMap()
    return raw.split(",").mapNotNull { token ->
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return@mapNotNull null

        // Split off optional username: user@host:port
        val atIdx = trimmed.indexOf('@')
        val username: String?
        val hostPort: String
        if (atIdx != -1) {
            username = trimmed.substring(0, atIdx).takeIf { it.isNotEmpty() }
            hostPort = trimmed.substring(atIdx + 1)
        } else {
            username = null
            hostPort = trimmed
        }

        // Split host:port — only treat the suffix as a port if it's a valid port number,
        // so that plain hostnames with dots (e.g. server.example.com) are never mangled.
        val lastColon = hostPort.lastIndexOf(':')
        val host: String
        val port: Int
        if (lastColon == -1) {
            host = hostPort
            port = 22
        } else {
            val possiblePort = hostPort.substring(lastColon + 1).toIntOrNull()
            if (possiblePort != null && possiblePort in 1..65535) {
                host = hostPort.substring(0, lastColon)
                port = possiblePort
            } else {
                host = hostPort
                port = 22
            }
        }

        JumpHost(host, port, username, keysByHost["$host:$port"])
    }
}
