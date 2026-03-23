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
     */
    suspend fun openShell(
        params: SshConnectionParams,
        termType: String = "xterm-256color",
        columns: Int = 80,
        rows: Int = 24,
        onHostKeyVerify: (hostname: String, fingerprint: String) -> Boolean,
    ): ShellSession = withContext(Dispatchers.IO) {

        val (session, jumpSessions, newJumpKeyLines) = createSession(params, onHostKeyVerify)

        val channel = session.openChannel("shell") as ChannelShell
        channel.setPtyType(termType)
        channel.setPtySize(columns, rows, columns * 8, rows * 16)
        channel.setAgentForwarding(params.agentForwarding)

        // Stdin: use setInputStream so JSch reads from our pipe and forwards to server.
        val stdinIn  = java.io.PipedInputStream(4096)
        val stdinOut = java.io.PipedOutputStream(stdinIn)
        channel.setInputStream(stdinIn)

        // Stdout: initialise JSch's internal pipe BEFORE connecting so no bytes are lost.
        val channelInput = channel.inputStream

        channel.connect(10_000)

        val hostKeyLine = buildKnownHostsLine(session.hostKey)
        ShellSession(session, channel, channelInput, stdinOut, params.hostname, hostKeyLine, jumpSessions, newJumpKeyLines)
    }

    /**
     * Opens an SFTP session.
     */
    suspend fun openSftp(
        params: SshConnectionParams,
        onHostKeyVerify: (hostname: String, fingerprint: String) -> Boolean,
    ): SftpSession = withContext(Dispatchers.IO) {

        val (session, jumpSessions, newJumpKeyLines) = createSession(params, onHostKeyVerify)

        val channel = session.openChannel("sftp") as com.jcraft.jsch.ChannelSftp
        channel.connect(10_000)

        val homePath = runCatching { channel.pwd() }.getOrDefault("/")
        val hostKeyLine = buildKnownHostsLine(session.hostKey)
        SftpSession(session, channel, params.hostname, hostKeyLine, jumpSessions, newJumpKeyLines, homePath)
    }

    private data class SessionResult(
        val session: Session,
        val jumpSessions: List<Session>,
        val newJumpKeyLines: List<String>,
    )

    /**
     * Shared session setup: auth, known hosts, config, keepalives, connect.
     */
    private fun createSession(
        params: SshConnectionParams,
        onHostKeyVerify: (hostname: String, fingerprint: String) -> Boolean,
    ): SessionResult {
        // ── 1. Build jump-host chain ────────────────────────────────────────────
        val jumpSessions = mutableListOf<Session>()
        val newJumpKeyLines = mutableListOf<String>()
        var proxy: com.jcraft.jsch.Proxy? = null

        for (jump in params.jumpHosts) {
            val jumpJsch = JSch()
            // Jump hosts use the same auth as the target
            if (params.auth is SshAuth.PublicKey) {
                jumpJsch.addIdentity(
                    "key",
                    params.auth.privateKeyPem.toByteArray(),
                    null,
                    params.auth.passphrase?.toByteArray(),
                )
            }
            if (!jump.knownHostsEntry.isNullOrBlank()) {
                jumpJsch.setKnownHosts(ByteArrayInputStream(jump.knownHostsEntry.toByteArray()))
            }

            val jumpSession = jumpJsch.getSession(jump.username ?: params.username, jump.host, jump.port)
            if (proxy != null) jumpSession.setProxy(proxy)

            jumpSession.setUserInfo(object : UserInfo {
                private var passwordUsed = false
                override fun getPassphrase(): String? = null
                override fun getPassword(): String? = (params.auth as? SshAuth.Password)?.password
                override fun promptPassword(message: String?): Boolean {
                    if (passwordUsed) return false
                    passwordUsed = true
                    return params.auth is SshAuth.Password
                }
                override fun promptPassphrase(message: String?) = false
                override fun promptYesNo(message: String?): Boolean {
                    val fp = message?.lines()
                        ?.firstOrNull { it.contains("fingerprint") || it.contains("SHA256") || it.contains("MD5") }
                        ?: message ?: "unknown"
                    return onHostKeyVerify(jump.host, fp)
                }
                override fun showMessage(message: String?) {}
            })

            val jumpConfig = Properties().apply {
                setProperty("StrictHostKeyChecking", if (jump.knownHostsEntry.isNullOrBlank()) "ask" else "yes")
                setProperty("PreferredAuthentications", when (params.auth) {
                    is SshAuth.PublicKey -> "publickey"
                    is SshAuth.Password  -> "password"
                })
                setProperty("HashKnownHosts", "no")
                setProperty("TCPKeepAlive", "yes")
            }
            jumpSession.setConfig(jumpConfig)
            jumpSession.setServerAliveInterval(30_000)
            jumpSession.setServerAliveCountMax(Int.MAX_VALUE)

            jumpSession.connect(20_000)
            jumpSession.setTimeout(0)

            // Collect the host key so we can persist it if it was unknown
            if (jump.knownHostsEntry.isNullOrBlank()) {
                newJumpKeyLines.add(buildKnownHostsLine(jumpSession.hostKey))
            }

            jumpSessions.add(jumpSession)

            // Create a proxy that tunnels through this jump session to the next hop
            proxy = JumpProxy(jumpSession)
        }

        // ── 2. Connect the real target session (possibly through the jump chain) ──
        val jsch = JSch()

        if (params.auth is SshAuth.PublicKey) {
            jsch.addIdentity(
                "key",
                params.auth.privateKeyPem.toByteArray(),
                null,
                params.auth.passphrase?.toByteArray(),
            )
        }

        if (!params.knownHostsEntry.isNullOrBlank()) {
            jsch.setKnownHosts(ByteArrayInputStream(params.knownHostsEntry.toByteArray()))
        }

        val session = jsch.getSession(params.username, params.hostname, params.port)
        if (proxy != null) session.setProxy(proxy)

        // Tag the session with the jump sessions so ShellSession/SftpSession can clean them up
        session.setUserInfo(object : UserInfo {
            private var passwordUsed = false
            override fun getPassphrase(): String? = null
            override fun getPassword(): String? = (params.auth as? SshAuth.Password)?.password
            override fun promptPassword(message: String?): Boolean {
                if (passwordUsed) return false
                passwordUsed = true
                return params.auth is SshAuth.Password
            }
            override fun promptPassphrase(message: String?) = false
            override fun promptYesNo(message: String?): Boolean {
                val fp = message?.lines()
                    ?.firstOrNull { it.contains("fingerprint") || it.contains("SHA256") || it.contains("MD5") }
                    ?: message ?: "unknown"
                return onHostKeyVerify(params.hostname, fp)
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
            setProperty("TCPKeepAlive", "yes")
        }
        session.setConfig(config)
        session.setServerAliveInterval(30_000)
        session.setServerAliveCountMax(Int.MAX_VALUE)

        // Bug in JSch mwiede 0.2.19: ChannelSession.setAgentForwarding(true) sets only the
        // channel-level flag but never sets Session.agent_forwarding. Fix via reflection.
        if (params.agentForwarding) {
            try {
                val f = session.javaClass.getDeclaredField("agent_forwarding")
                f.isAccessible = true
                f.setBoolean(session, true)
            } catch (_: Exception) {}
        }

        session.connect(20_000)
        // JSch leaves a residual socket read timeout from the connect phase — reset to infinite.
        session.setTimeout(0)

        return SessionResult(session, jumpSessions, newJumpKeyLines)
    }

    /** JSch [Proxy] implementation that tunnels through an already-connected SSH [Session]. */
    private class JumpProxy(private val via: Session) : com.jcraft.jsch.Proxy {
        private var channel: com.jcraft.jsch.ChannelDirectTCPIP? = null

        override fun connect(sf: com.jcraft.jsch.SocketFactory?, host: String, port: Int, timeout: Int) {
            val ch = via.openChannel("direct-tcpip") as com.jcraft.jsch.ChannelDirectTCPIP
            ch.setHost(host)
            ch.setPort(port)
            ch.setOrgIPAddress("127.0.0.1")
            ch.setOrgPort(0)
            ch.connect(if (timeout <= 0) 20_000 else timeout)
            channel = ch
        }

        override fun getInputStream(): java.io.InputStream = channel!!.inputStream
        override fun getOutputStream(): java.io.OutputStream = channel!!.outputStream
        override fun getSocket(): java.net.Socket? = null
        override fun close() { runCatching { channel?.disconnect() } }
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
    private val jumpSessions: List<Session> = emptyList(),
    /**
     * Known-hosts lines for jump hosts that had no prior stored key (empty on subsequent connects).
     * Callers should persist these so the user is not prompted again next time.
     */
    val newJumpHostKeyLines: List<String> = emptyList(),
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
        jumpSessions.reversed().forEach { runCatching { it.disconnect() } }
    }
}

/** A single entry returned by [SftpSession.listDir]. */
data class SftpEntry(
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modTimeSeconds: Int,   // Unix timestamp
)

/** A live SFTP session. */
class SftpSession(
    private val session: Session,
    private val channel: com.jcraft.jsch.ChannelSftp,
    val hostname: String,
    val hostKeyLine: String,
    private val jumpSessions: List<Session> = emptyList(),
    /** See [ShellSession.newJumpHostKeyLines]. */
    val newJumpHostKeyLines: List<String> = emptyList(),
    /** The working directory at the time the channel was opened (i.e. the user's home). */
    val homePath: String = "/",
) {
    val isConnected get() = channel.isConnected && session.isConnected

    /** Lists [path], returning entries sorted: dirs first, then files, both alphabetically. */
    @Suppress("UNCHECKED_CAST")
    fun listDir(path: String): List<SftpEntry> {
        val raw = channel.ls(path) as Collection<com.jcraft.jsch.ChannelSftp.LsEntry>
        return raw
            .filter { it.filename != "." && it.filename != ".." }
            .map { e ->
                SftpEntry(
                    name           = e.filename,
                    isDir          = e.attrs.isDir,
                    size           = e.attrs.size,
                    modTimeSeconds = e.attrs.mTime,
                )
            }
            .sortedWith(compareByDescending<SftpEntry> { it.isDir }.thenBy { it.name.lowercase() })
    }

    /**
     * Downloads [remotePath] into [dest], calling [onProgress] with cumulative bytes received.
     * Runs synchronously — call from an IO coroutine.
     */
    fun downloadFile(
        remotePath: String,
        dest: java.io.OutputStream,
        onProgress: (bytesReceived: Long) -> Unit = {},
    ) {
        var received = 0L
        channel.get(remotePath, dest, object : com.jcraft.jsch.SftpProgressMonitor {
            override fun init(op: Int, src: String?, dest: String?, max: Long) {}
            override fun count(count: Long): Boolean { received += count; onProgress(received); return true }
            override fun end() {}
        })
    }

    /**
     * Uploads [src] to [remotePath], calling [onProgress] with cumulative bytes sent.
     * Runs synchronously — call from an IO coroutine.
     */
    fun uploadFile(
        src: java.io.InputStream,
        remotePath: String,
        onProgress: (bytesSent: Long) -> Unit = {},
    ) {
        var sent = 0L
        channel.put(src, remotePath, object : com.jcraft.jsch.SftpProgressMonitor {
            override fun init(op: Int, src: String?, dest: String?, max: Long) {}
            override fun count(count: Long): Boolean { sent += count; onProgress(sent); return true }
            override fun end() {}
        }, com.jcraft.jsch.ChannelSftp.OVERWRITE)
    }

    fun deleteFile(remotePath: String) = channel.rm(remotePath)
    fun deleteDir(remotePath: String)  = channel.rmdir(remotePath)
    fun rename(oldPath: String, newPath: String) = channel.rename(oldPath, newPath)
    fun mkdir(remotePath: String)      = channel.mkdir(remotePath)

    fun disconnect() {
        runCatching { channel.disconnect() }
        runCatching { session.disconnect() }
        jumpSessions.reversed().forEach { runCatching { it.disconnect() } }
    }
}
