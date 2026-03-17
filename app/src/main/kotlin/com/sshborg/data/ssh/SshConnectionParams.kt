package com.sshborg.data.ssh

/** All parameters needed to open an SSH connection. */
data class SshConnectionParams(
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val auth: SshAuth,
    val agentForwarding: Boolean = false,
    /** If null, fingerprint verification is skipped (only on first connect) */
    val knownHostsEntry: String? = null,
)

sealed interface SshAuth {
    data class Password(val password: String) : SshAuth
    data class PublicKey(val privateKeyPem: String, val passphrase: String? = null) : SshAuth
}
