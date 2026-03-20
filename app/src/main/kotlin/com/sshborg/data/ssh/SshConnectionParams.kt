package com.sshborg.data.ssh

/** A single jump host in a ProxyJump chain. */
data class JumpHost(
    val host: String,
    val port: Int,
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
 * Parses a jump-hosts string ("host1:22,host2:port") and a newline-delimited known_hosts blob
 * into a list of [JumpHost].
 */
fun parseJumpHosts(raw: String?, knownKeysBlob: String?): List<JumpHost> {
    if (raw.isNullOrBlank()) return emptyList()
    // Build a map: hostname -> known_hosts line
    val keysByHost: Map<String, String> = knownKeysBlob
        ?.lines()
        ?.filter { it.isNotBlank() }
        ?.associateBy { it.substringBefore(" ") }
        ?: emptyMap()
    return raw.split(",").mapNotNull { token ->
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return@mapNotNull null
        val lastColon = trimmed.lastIndexOf(':')
        val host: String
        val port: Int
        if (lastColon == -1 || trimmed.indexOf(':') == lastColon && trimmed.contains('.')) {
            // No colon, or single colon that is part of IPv4 — treat full token as host
            host = trimmed
            port = 22
        } else {
            host = trimmed.substring(0, lastColon)
            port = trimmed.substring(lastColon + 1).toIntOrNull() ?: 22
        }
        JumpHost(host, port, keysByHost[host])
    }
}
