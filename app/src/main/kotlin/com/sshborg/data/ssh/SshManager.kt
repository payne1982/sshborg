package com.sshborg.data.ssh

import com.jcraft.jsch.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.Properties

/**
 * Wraps JSch to provide coroutine-friendly SSH sessions.
 */
object SshManager {

    /**
     * Opens an interactive shell session.
     *
     * @param onHostKeyVerify called with (hostname, fingerprint) when the host is not in known_hosts.
     *   Return true to accept (and cache), false to reject.
     * @return [ShellSession] on success; throws [JSchException] on failure.
     */
    suspend fun openShell(
        params: SshConnectionParams,
        termType: String = "xterm-256color",
        columns: Int = 80,
        rows: Int = 24,
        onHostKeyVerify: (hostname: String, fingerprint: String) -> Boolean,
    ): ShellSession = withContext(Dispatchers.IO) {

        val jsch = JSch()

        // Auth: load identity for key auth
        if (params.auth is SshAuth.PublicKey) {
            jsch.addIdentity(
                "key",
                params.auth.privateKeyPem.toByteArray(),
                null,
                params.auth.passphrase?.toByteArray(),
            )
        }

        // Known hosts: feed in-memory as a stream in known_hosts format
        if (!params.knownHostsEntry.isNullOrBlank()) {
            jsch.setKnownHosts(ByteArrayInputStream(params.knownHostsEntry.toByteArray()))
        }

        val session = jsch.getSession(params.username, params.hostname, params.port)

        // Capture fingerprint for the verify callback
        var capturedFingerprint = ""

        session.setUserInfo(object : UserInfo {
            override fun getPassphrase(): String? = null
            override fun getPassword(): String? = (params.auth as? SshAuth.Password)?.password
            override fun promptPassword(message: String?) = params.auth is SshAuth.Password
            override fun promptPassphrase(message: String?) = false
            override fun promptYesNo(message: String?): Boolean {
                // JSch passes the fingerprint inside the message for unknown hosts
                capturedFingerprint = message?.lines()
                    ?.firstOrNull { it.contains("fingerprint") || it.contains("SHA256") || it.contains("MD5") }
                    ?: message ?: "unknown"
                return onHostKeyVerify(params.hostname, capturedFingerprint)
            }
            override fun showMessage(message: String?) {}
        })

        val config = Properties().apply {
            setProperty("StrictHostKeyChecking", "ask")
            setProperty("PreferredAuthentications", when (params.auth) {
                is SshAuth.PublicKey -> "publickey"
                is SshAuth.Password  -> "password"
            })
            setProperty("HashKnownHosts", "no")
        }
        session.setConfig(config)
        session.connect(20_000)

        val channel = session.openChannel("shell") as ChannelShell
        channel.setPtyType(termType)
        channel.setPtySize(columns, rows, columns * 8, rows * 16)
        channel.setAgentForwarding(params.agentForwarding)

        // Stdin: use setInputStream so JSch reads from our pipe and forwards to server.
        // This is more reliable than channel.getOutputStream() in the mwiede JSch fork.
        val stdinIn  = java.io.PipedInputStream(4096)
        val stdinOut = java.io.PipedOutputStream(stdinIn)
        channel.setInputStream(stdinIn)

        // Stdout: initialise JSch's internal pipe BEFORE connecting so no bytes are lost.
        val channelInput = channel.inputStream

        channel.connect(10_000)

        // Capture the host key string for storage in DB
        val hostKey = session.hostKey
        val hostKeyLine = buildKnownHostsLine(hostKey)

        ShellSession(session, channel, channelInput, stdinOut, params.hostname, hostKeyLine)
    }

    /**
     * Generates a new SSH key pair.
     * @return Pair(privateKeyPem, publicKeyOpenSSH)
     *
     * Ed25519 uses BouncyCastle directly because JSch's KeyPairEdDSA.getPrivateKey()
     * throws UnsupportedOperationException when serialising the key.
     * RSA and ECDSA continue to use JSch which handles them correctly.
     */
    suspend fun generateKeyPair(
        type: String = "ed25519",
        comment: String = "",
        passphrase: ByteArray? = null,
        bits: Int? = null,
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        when (type.lowercase()) {
            "ed25519" -> generateEd25519WithBc(comment)
            else      -> generateWithJsch(type, comment, passphrase, bits)
        }
    }

    private fun generateEd25519WithBc(comment: String): Pair<String, String> {
        val gen = org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator()
        gen.init(org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters(java.security.SecureRandom()))
        val kp = gen.generateKeyPair()
        val priv = kp.private as org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
        val pub  = kp.public  as org.bouncycastle.crypto.params.Ed25519PublicKeyParameters

        // Private key in OpenSSH format (understood by all modern SSH clients)
        val privBytes = org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil.encodePrivateKey(priv)
        val b64 = java.util.Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(privBytes)
        val privPem = "-----BEGIN OPENSSH PRIVATE KEY-----\n$b64\n-----END OPENSSH PRIVATE KEY-----\n"

        // Public key in OpenSSH wire format → base64
        val pubBytes = org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil.encodePublicKey(pub)
        val pubStr = "ssh-ed25519 ${java.util.Base64.getEncoder().encodeToString(pubBytes)}" +
            if (comment.isNotEmpty()) " $comment" else ""

        return privPem to pubStr
    }

    private fun generateWithJsch(type: String, comment: String, passphrase: ByteArray?, bits: Int?): Pair<String, String> {
        val jsch = JSch()
        val isRsa = type.lowercase() == "rsa"
        val keyType = if (isRsa) KeyPair.RSA else KeyPair.ECDSA
        // For RSA: use requested bits (default 4096). For ECDSA: bits maps to curve (256→nistp256, 384→nistp384, 521→nistp521).
        val kp = if (isRsa) {
            KeyPair.genKeyPair(jsch, keyType, bits ?: 4096)
        } else {
            KeyPair.genKeyPair(jsch, keyType, bits ?: 256)
        }
        val privOut = java.io.ByteArrayOutputStream()
        if (passphrase != null) kp.writePrivateKey(privOut, passphrase) else kp.writePrivateKey(privOut)
        val pubOut = java.io.ByteArrayOutputStream()
        kp.writePublicKey(pubOut, comment)
        kp.dispose()
        return privOut.toString(Charsets.UTF_8) to pubOut.toString(Charsets.UTF_8)
    }

    /** Builds a known_hosts line from a JSch HostKey. */
    fun buildKnownHostsLine(hostKey: HostKey): String =
        "${hostKey.host} ${hostKey.type} ${hostKey.getKey()}"
}

/** A live interactive SSH shell. */
class ShellSession(
    private val session: Session,
    private val channel: ChannelShell,
    private val channelInput: java.io.InputStream,
    private val stdinOutput: java.io.OutputStream,
    val hostname: String,
    /** Known-hosts line to persist in DB after first successful connect. */
    val hostKeyLine: String,
) {
    val inputStream: java.io.InputStream get() = channelInput
    val outputStream: java.io.OutputStream get() = stdinOutput
    val isConnected get() = channel.isConnected && session.isConnected
    val exitStatus get() = channel.exitStatus

    fun resize(columns: Int, rows: Int) {
        channel.setPtySize(columns, rows, columns * 8, rows * 16)
    }

    fun disconnect() {
        runCatching { channel.disconnect() }
        runCatching { session.disconnect() }
    }
}
