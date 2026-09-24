package com.sshborg.data.ssh

import com.sshborg.BuildConfig
import com.jcraft.jsch.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import java.util.Properties

/**
 * Wraps JSch to provide coroutine-friendly SSH sessions.
 */
object SshManager {

    /**
     * Blocking JSch calls run here rather than in the caller's scope. A socket read cannot be
     * interrupted, so a connect stuck in one would hold a cancelled caller until it returned —
     * which, through a jump host, is never. Off the caller's job tree the caller leaves at
     * once and [ConnectGuard.abort] takes the half-built chain down behind it.
     */
    private val connectScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** How often the watchdog looks at the clock. */
    private const val DEADLINE_TICK_MS = 250L

    /** How long the liveness probe waits for the jump host to grant a channel. */
    private const val PROBE_MS = 5_000

    /**
     * Holds the sessions an attempt has opened so far, so they can be closed from outside.
     *
     * Closing them is the only way to end a stuck connect: JSch applies its read timeout only
     * when [com.jcraft.jsch.Proxy.getSocket] hands it a real socket, and a hop tunnelled
     * through [JumpProxy] has none — so the handshake with the final host of a jump chain
     * waits on a pipe with no deadline of any kind.
     */
    private class ConnectGuard(
        private val onHostKeyVerify: (hostname: String, fingerprint: String, keyLine: String) -> Boolean,
    ) {
        private val sessions = mutableListOf<Session>()
        private val jumpHops = mutableListOf<Session>()
        private var aborted = false

        /** Set while a host-key dialog is up, so the user's own thinking time isn't counted. */
        @Volatile var waitingForUser = false

        /** True when the watchdog, and not the caller, ended the attempt. */
        @Volatile var timedOut = false

        /** What the attempt was last seen doing, and the thread doing it. */
        @Volatile private var stage = "starting"
        @Volatile private var worker: Thread? = null
        @Volatile private var stuck: Throwable? = null

        /** What the liveness probe found, when one was run. */
        @Volatile private var jumpAnswered: Boolean? = null

        /**
         * Marks the stage the attempt has reached. Cheap on purpose — it runs on the connect
         * path — and it is the only thing that can tell the four ways a jump connect stalls
         * apart afterwards: the TCP connect to the bastion, the bastion's own handshake, the
         * tunnel being opened, and the handshake with the final host.
         */
        fun at(stage: String) {
            this.stage = stage
            worker = Thread.currentThread()
        }

        /**
         * Photographs the blocked thread. Must run *before* [abort], which unblocks it: a
         * moment later the interesting frame is gone and the stack shows the unwinding.
         */
        fun captureStuckFrame() {
            val frames = worker?.stackTrace ?: return
            stuck = Throwable("blocked while $stage").apply { stackTrace = frames }
        }

        /**
         * Asks the last jump hop for a channel, to find out whether it is still talking to us
         * at all. It answers the one question the stack alone cannot: a stall waiting for the
         * final host means either that the host stayed silent, or that the way back from the
         * jump host closed while we waited — the same symptom, two different machines at
         * fault. Must run before [abort], while the hop is still connected.
         */
        fun probeJumpLiveness() {
            val hop = synchronized(this) { jumpHops.lastOrNull() } ?: return
            jumpAnswered = runCatching {
                val channel = hop.openChannel("session")
                try { channel.connect(PROBE_MS) } finally { runCatching { channel.disconnect() } }
                true
            }.getOrDefault(false)
        }

        /** The failure to report, carrying the stage in its message and the frames as cause. */
        val timeoutError: JSchException
            get() {
                val verdict = when (jumpAnswered) {
                    true  -> " (the jump host was still answering, the host itself was not)"
                    false -> " (the jump host had stopped answering too)"
                    null  -> ""
                }
                return JSchException("Connection timed out while $stage$verdict", stuck)
            }

        /** The host-key callback, wrapped so it stops the clock for as long as it blocks. */
        val verify: (String, String, String) -> Boolean = { hostname, fingerprint, keyLine ->
            waitingForUser = true
            try { onHostKeyVerify(hostname, fingerprint, keyLine) } finally { waitingForUser = false }
        }

        @Synchronized fun track(session: Session) {
            if (aborted) runCatching { session.disconnect() } else sessions.add(session)
        }

        /** As [track], but the hop is also the one the liveness probe will question. */
        @Synchronized fun trackJump(session: Session) {
            track(session)
            if (!aborted) jumpHops.add(session)
        }

        /** Closes everything opened so far; safe to call more than once. */
        @Synchronized fun abort() {
            aborted = true
            sessions.forEach { runCatching { it.disconnect() } }
            sessions.clear()
            jumpHops.clear()
        }
    }

    /**
     * Runs one connect attempt under a deadline, off the caller's job tree.
     *
     * Two things end it early: the watchdog, when the attempt has spent its budget, and the
     * caller being cancelled — the X in the connecting overlay. Both land on
     * [ConnectGuard.abort]: closing the sockets is what unblocks the JSch thread, which no
     * cancellation can reach on its own, and it also keeps a cancelled attempt from leaving an
     * authenticated session behind on the jump host.
     */
    private suspend fun <T> guardedConnect(
        params: SshConnectionParams,
        onHostKeyVerify: (hostname: String, fingerprint: String, keyLine: String) -> Boolean,
        block: suspend (ConnectGuard) -> T,
    ): T {
        val guard = ConnectGuard(onHostKeyVerify)
        // A hop's worth of handshake on top of the base budget for every jump in the chain.
        val budgetMs = 30_000L + 15_000L * params.jumpHosts.size
        // One attempt, deliberately: retrying automatically would paper over the stall while
        // we are still working out which machine causes it, and a failure nobody sees is a
        // failure nobody can diagnose. The retry belongs here once that is settled.
        val work = connectScope.async { block(guard) }
        val watchdog = connectScope.launch {
            var left = budgetMs
            while (left > 0) {
                delay(DEADLINE_TICK_MS)
                if (!guard.waitingForUser) left -= DEADLINE_TICK_MS
            }
            if (!work.isActive) return@launch
            guard.timedOut = true
            guard.captureStuckFrame()
            guard.probeJumpLiveness()
            guard.abort()
        }
        try {
            val session = work.await()
            // The watchdog can fire in the instant the attempt finishes; it is already closing
            // what we are holding, so don't hand the caller a session on its way out.
            if (guard.timedOut) throw guard.timeoutError
            return session
        } catch (e: Throwable) {
            guard.abort()
            work.cancel()
            if (guard.timedOut && e !is CancellationException) throw guard.timeoutError
            throw e
        } finally {
            watchdog.cancel()
        }
    }

    /**
     * Opens an interactive shell session.
     */
    suspend fun openShell(
        params: SshConnectionParams,
        termType: String = "xterm-256color",
        columns: Int = 80,
        rows: Int = 24,
        onHostKeyVerify: (hostname: String, fingerprint: String, keyLine: String) -> Boolean,
    ): ShellSession = guardedConnect(params, onHostKeyVerify) { guard -> runInterruptible {

        val (session, jumpSessions, newJumpKeyLines, newJumpHostKeyUpdates) = createSession(params, guard)

        val channel = session.openChannel("shell") as ChannelShell
        channel.setPtyType(termType)
        channel.setPtySize(columns, rows, columns * 8, rows * 16)
        channel.setAgentForwarding(params.agentForwarding)

        // Stdout: initialise JSch's internal pipe BEFORE connecting so no bytes are lost.
        val channelInput = channel.inputStream

        // Stdin: write straight to the channel's output stream. We deliberately do NOT use
        // channel.setInputStream(PipedInputStream): that spawns a JSch helper thread that
        // reads our pipe, and PipedInputStream tracks the *writer* thread. Since each
        // sendInput() writes from a fresh Dispatchers.IO coroutine, once such a transient
        // thread is reclaimed by the pool the JSch stdin thread (blocked in read()) throws
        // "Pipe broken" and dies — silently killing input on a backgrounded session after it
        // is resumed, while the channel still reports connected. Writing directly avoids the
        // helper thread and the writer-thread tracking entirely.
        val channelOutput = channel.outputStream

        guard.at("opening the shell")
        channel.connect(10_000)

        val hostKeyLine = buildKnownHostsLine(session.hostKey)
        ShellSession(session, channel, channelInput, channelOutput, params.hostname, hostKeyLine, jumpSessions, newJumpKeyLines, newJumpHostKeyUpdates)
    } }

    /**
     * Opens an SFTP session.
     */
    suspend fun openSftp(
        params: SshConnectionParams,
        onHostKeyVerify: (hostname: String, fingerprint: String, keyLine: String) -> Boolean,
    ): SftpSession = guardedConnect(params, onHostKeyVerify) { guard ->

        val (session, jumpSessions, newJumpKeyLines, newJumpHostKeyUpdates) =
            runInterruptible { createSession(params, guard) }

        val channel = runInterruptible {
            guard.at("opening the SFTP channel")
            (session.openChannel("sftp") as com.jcraft.jsch.ChannelSftp).also { it.connect(10_000) }
        }

        // Tighter than the attempt's own deadline: the transport is up by now, so a pwd that
        // doesn't come back means the SFTP channel itself is wedged.
        val homePath = withTimeoutOrNull(5_000) {
            runInterruptible { guard.at("reading the remote home directory"); runCatching { channel.pwd() }.getOrDefault("/") }
        } ?: run {
            guard.captureStuckFrame()
            guard.abort()
            throw guard.timeoutError
        }
        val hostKeyLine = buildKnownHostsLine(session.hostKey)
        SftpSession(session, channel, params.hostname, hostKeyLine, jumpSessions, newJumpKeyLines, newJumpHostKeyUpdates, homePath)
    }

    private data class SessionResult(
        val session: Session,
        val jumpSessions: List<Session>,
        /** New host-key lines for simple-mode jump hops (no prior stored key). */
        val newJumpKeyLines: List<String>,
        /**
         * New host-key lines for host-list-mode jump hops: pairs of (hostId, keyLine).
         * The caller should persist each keyLine to the corresponding HostEntity.
         */
        val newJumpHostKeyUpdates: List<Pair<Long, String>>,
    )

    /**
     * Shared session setup: auth, known hosts, config, keepalives, connect.
     */
    private fun createSession(
        params: SshConnectionParams,
        guard: ConnectGuard,
    ): SessionResult {
        // ── 1. Build jump-host chain ────────────────────────────────────────────
        val jumpSessions = mutableListOf<Session>()
        val newJumpKeyLines = mutableListOf<String>()
        val newJumpHostKeyUpdates = mutableListOf<Pair<Long, String>>()
        var proxy: com.jcraft.jsch.Proxy? = null

        for ((hop, jump) in params.jumpHosts.withIndex()) {
            // Use per-hop auth if provided (host-list mode), otherwise fall back to target auth
            val jumpAuth = jump.auth ?: params.auth

            val jumpJsch = JSch()
            if (jumpAuth is SshAuth.PublicKey) {
                jumpJsch.addIdentity(
                    "key",
                    jumpAuth.privateKeyPem.toByteArray(),
                    null,
                    jumpAuth.passphrase?.toByteArray(),
                )
            }
            if (!jump.knownHostsEntry.isNullOrBlank()) {
                jumpJsch.setKnownHosts(ByteArrayInputStream(jump.knownHostsEntry.toByteArray()))
            }

            val jumpSession = jumpJsch.getSession(jump.username ?: params.username, jump.host, jump.port)
            guard.trackJump(jumpSession)
            if (proxy != null) jumpSession.setProxy(proxy)

            jumpSession.setUserInfo(object : UserInfo, UIKeyboardInteractive {
                private var passwordUsed = false
                private var kbiUsed = false
                override fun getPassphrase(): String? = null
                override fun getPassword(): String? = (jumpAuth as? SshAuth.Password)?.password
                override fun promptPassword(message: String?): Boolean {
                    if (passwordUsed) return false
                    passwordUsed = true
                    return jumpAuth is SshAuth.Password
                }
                override fun promptPassphrase(message: String?) = false
                override fun promptYesNo(message: String?): Boolean {
                    val fp = message?.lines()
                        ?.firstOrNull { it.contains("fingerprint") || it.contains("SHA256") || it.contains("MD5") }
                        ?: message ?: "unknown"
                    return guard.verify(jump.host, fp, buildKnownHostsLine(jumpSession.hostKey))
                }
                override fun showMessage(message: String?) {}
                override fun promptKeyboardInteractive(
                    destination: String?, name: String?, instruction: String?,
                    prompt: Array<out String>?, echo: BooleanArray?,
                ): Array<String>? {
                    val pw = (jumpAuth as? SshAuth.Password)?.password ?: return null
                    if (prompt.isNullOrEmpty()) return arrayOf()
                    if (kbiUsed) return null
                    kbiUsed = true
                    return Array(prompt.size) { pw }
                }
            })

            val jumpConfig = Properties().apply {
                setProperty("StrictHostKeyChecking", if (jump.knownHostsEntry.isNullOrBlank()) "ask" else "yes")
                setProperty("PreferredAuthentications", when (jumpAuth) {
                    is SshAuth.PublicKey -> "publickey"
                    is SshAuth.Password  -> "password,keyboard-interactive"
                })
                setProperty("HashKnownHosts", "no")
                setProperty("TCPKeepAlive", "yes")
                if (params.allowLegacyCiphers) applyLegacyCiphers(this)
            }
            jumpSession.setConfig(jumpConfig)
            jumpSession.setServerAliveInterval(15_000)
            jumpSession.setServerAliveCountMax(20)

            // The read timeout connect() leaves behind is deliberately kept — see the note on
            // the target session below.
            guard.at(if (proxy == null) "connecting to jump host ${hop + 1}"
                     else "connecting to jump host ${hop + 1} through the previous hop")
            jumpSession.connect(20_000)

            // Collect the host key so we can persist it if it was unknown
            if (jump.knownHostsEntry.isNullOrBlank()) {
                val keyLine = buildKnownHostsLine(jumpSession.hostKey)
                if (jump.hostId != null) {
                    // Host-list mode: associate key update with the jump host entity
                    newJumpHostKeyUpdates.add(jump.hostId to keyLine)
                } else {
                    // Simple mode: accumulate in list; ViewModel saves to target's jumpHostKeys
                    newJumpKeyLines.add(keyLine)
                }
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
        guard.track(session)
        if (proxy != null) session.setProxy(proxy)

        // Tag the session with the jump sessions so ShellSession/SftpSession can clean them up
        session.setUserInfo(object : UserInfo, UIKeyboardInteractive {
            private var passwordUsed = false
            private var kbiUsed = false
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
                return guard.verify(params.hostname, fp, buildKnownHostsLine(session.hostKey))
            }
            override fun showMessage(message: String?) {}
            // Servers with `PasswordAuthentication no` but `KbdInteractiveAuthentication yes`
            // deliver the typed password over keyboard-interactive; answer its prompt(s) with it.
            override fun promptKeyboardInteractive(
                destination: String?, name: String?, instruction: String?,
                prompt: Array<out String>?, echo: BooleanArray?,
            ): Array<String>? {
                val pw = (params.auth as? SshAuth.Password)?.password ?: return null
                if (prompt.isNullOrEmpty()) return arrayOf()   // info-only round: continue
                if (kbiUsed) return null                        // don't retry a wrong password forever
                kbiUsed = true
                return Array(prompt.size) { pw }
            }
        })

        val config = Properties().apply {
            setProperty("StrictHostKeyChecking", "ask")
            setProperty("PreferredAuthentications", when (params.auth) {
                is SshAuth.PublicKey -> "publickey"
                is SshAuth.Password  -> "password,keyboard-interactive"
            })
            setProperty("HashKnownHosts", "no")
            setProperty("TCPKeepAlive", "yes")
            if (params.allowLegacyCiphers) applyLegacyCiphers(this)
        }
        session.setConfig(config)
        // Keepalive every 15s, give up after 20 missed (~5 minutes). The two numbers answer
        // different questions. The interval keeps the path warm (see the WifiLock in
        // SshForegroundService) and wants to stay short. The count decides how long a silence
        // is tolerated before the session is declared dead, and wants to be generous: a tunnel
        // or a lift is a silence a TCP connection often survives, and until 2026-09 the count
        // never ran at all — setTimeout(0) after connect had disabled the mechanism — so no
        // session was ever given up this way. A connection that dies with an actual error
        // still reports immediately; this threshold only governs the silent, half-open case.
        // Note the count advances only while the app is running: a frozen process wakes to a
        // single expired read, not to one per minute spent frozen.
        session.setServerAliveInterval(15_000)
        session.setServerAliveCountMax(20)

        // Bug in JSch mwiede 0.2.19: ChannelSession.setAgentForwarding(true) sets only the
        // channel-level flag but never sets Session.agent_forwarding. Fix via reflection.
        if (params.agentForwarding) {
            try {
                val f = session.javaClass.getDeclaredField("agent_forwarding")
                f.isAccessible = true
                f.setBoolean(session, true)
            } catch (_: Exception) {}
        }

        guard.at(if (proxy == null) "connecting to the host"
                 else "connecting to the host through the jump chain")
        session.connect(20_000)
        // Deliberately no setTimeout(0) here. JSch drives its keepalive entirely off the socket
        // read timeout: the reader thread takes a SocketTimeoutException, sends a probe, and
        // gives up after serverAliveCountMax of them. connect() leaves that timeout at exactly
        // serverAliveInterval, so resetting it to infinite — which we did from before the
        // keepalives existed until 2026-09 — silently stopped every probe from ever being sent,
        // and a half-dead connection was then never detected.

        // Activate local port-forwarding rules (-L).
        guard.at("setting up port forwarding")
        for (pf in params.portForwardings) {
            session.setPortForwardingL(pf.bindAddress, pf.localPort, pf.remoteHost, pf.remotePort)
        }

        return SessionResult(session, jumpSessions, newJumpKeyLines, newJumpHostKeyUpdates)
    }

    /** JSch [Proxy] implementation that tunnels through an already-connected SSH [Session]. */
    private class JumpProxy(private val via: Session) : com.jcraft.jsch.Proxy {
        private var channel: com.jcraft.jsch.ChannelDirectTCPIP? = null
        private var tunnelIn: java.io.InputStream? = null
        private var tunnelOut: java.io.OutputStream? = null

        override fun connect(sf: com.jcraft.jsch.SocketFactory?, host: String, port: Int, timeout: Int) {
            val ch = via.openChannel("direct-tcpip") as com.jcraft.jsch.ChannelDirectTCPIP
            ch.setHost(host)
            ch.setPort(port)
            ch.setOrgIPAddress("127.0.0.1")
            ch.setOrgPort(0)
            // Both streams BEFORE connect, and not as a matter of taste: getInputStream() is
            // what installs the pipe that arriving channel data is written into, and until it
            // has run Channel.write() hits a NullPointerException on the missing stream and
            // *swallows it* — the bytes are dropped without a trace. The jump host confirms
            // the channel only once its own connection to the target is up, so the target's
            // SSH banner can be immediately behind that confirmation, in the same read: the
            // jump session's reader thread then hands both to the channel while our thread is
            // still on its way back out of connect(). Losing the banner deadlocks the
            // handshake — we go on waiting for a greeting that will not be sent twice, the
            // target waits for the key exchange we cannot start. JSch knows the trap and logs
            // "getInputStream() should be called before connect()" when it catches us late.
            tunnelIn = ch.inputStream
            tunnelOut = ch.outputStream
            ch.connect(if (timeout <= 0) 20_000 else timeout)
            channel = ch
        }

        override fun getInputStream(): java.io.InputStream = tunnelIn!!
        override fun getOutputStream(): java.io.OutputStream = tunnelOut!!
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
        // ByteArrayOutputStream.toString(Charset) needs API 33; the String overload works everywhere
        return privOut.toString(Charsets.UTF_8.name()) to pubOut.toString(Charsets.UTF_8.name())
    }

    /**
     * Loads a PEM or OpenSSH private key, unlocks it if it is encrypted, and returns it in a
     * form that needs no passphrase again.
     *
     * The passphrase is used here and **nowhere else**: it is never stored. A passphrase is a
     * secret people reuse — on other keys, on other machines — while a private key is worth only
     * itself, so keeping the key unlocked beside its own passphrase, which is what a stored
     * passphrase amounts to, would give away the second secret for nothing. What the app keeps
     * is the unlocked key, protected exactly like a key that never had a passphrase: the app's
     * private storage, and the Keystore blob when that setting is on.
     *
     * Not every encrypted key can be written back out — see [unlockedText] — and one that cannot
     * is refused rather than stored half-way, with `unsupported_encryption`.
     *
     * @param pem  private key text (PEM / OpenSSH format)
     * @param passphrase  only needed if the key is encrypted
     */
    fun importPrivateKey(pem: String, passphrase: String? = null): ImportedKey {
        val jsch = JSch()
        val normalizedPem = pem.replace("\r\n", "\n").trim()
        val kp = KeyPair.load(jsch, normalizedPem.toByteArray(Charsets.UTF_8), null)
        try {
            // Read before decrypting: a successful decrypt clears JSch's own flag, so asking
            // afterwards reports every key as unencrypted.
            val wasEncrypted = kp.isEncrypted
            if (wasEncrypted) {
                if (passphrase.isNullOrEmpty()) throw JSchException("encrypted")
                if (!kp.decrypt(passphrase.toByteArray(Charsets.UTF_8))) throw JSchException("wrong_passphrase")
            }
            val pubOut = java.io.ByteArrayOutputStream()
            kp.writePublicKey(pubOut, "")
            val pub = pubOut.toString(Charsets.UTF_8.name()).trim()
            val algToken = pub.substringBefore(" ")
            val keyType = when {
                algToken == "ssh-rsa"        -> "rsa"
                algToken == "ssh-ed25519"    -> "ed25519"
                algToken.startsWith("ecdsa") -> "ecdsa"
                else                         -> algToken
            }
            // An unencrypted key is stored as it arrived, byte for byte.
            val stored =
                if (!wasEncrypted) normalizedPem
                else unlockedText(kp, pub) ?: throw JSchException("unsupported_encryption")
            return ImportedKey(stored, pub, keyType)
        } finally {
            kp.dispose()
        }
    }

    /**
     * The text of an unlocked [kp], or null when it cannot be produced.
     *
     * Two routes, because JSch has no single one. `writePrivateKey` covers RSA, ECDSA and the
     * old PEM form, and throws for the rest — `UnsupportedOperationException` for ed25519, whose
     * private half it refuses to serialise, and a `NullPointerException` for a key that came out
     * of a PKCS#8 container. What those have in common is `forSSHAgent`, which is public and has
     * to contain the private material, since that is what an agent is handed; from an ed25519
     * blob the 32-byte seed is enough to write the file with the same BouncyCastle encoder
     * [generateEd25519WithBc] already uses.
     *
     * Whatever comes out is verified before it is trusted: it is loaded back, and its public key
     * must match [expectedPublicKey] exactly, with nothing left encrypted. So a mistake here
     * cannot produce a key that looks imported and is in fact something else — it produces null,
     * and the import is refused.
     */
    private fun unlockedText(kp: KeyPair, expectedPublicKey: String): String? {
        val candidate = writtenOut(kp) ?: fromAgentBlob(kp) ?: return null
        val reloaded = runCatching {
            KeyPair.load(JSch(), candidate.toByteArray(Charsets.UTF_8), null)
        }.getOrNull() ?: return null
        return try {
            if (reloaded.isEncrypted) return null
            val out = java.io.ByteArrayOutputStream()
            reloaded.writePublicKey(out, "")
            candidate.takeIf { out.toString(Charsets.UTF_8.name()).trim() == expectedPublicKey }
        } catch (_: Exception) {
            null
        } finally {
            reloaded.dispose()
        }
    }

    /** JSch's own serialisation, which works for some key types and throws for others. */
    private fun writtenOut(kp: KeyPair): String? = runCatching {
        val out = java.io.ByteArrayOutputStream()
        kp.writePrivateKey(out)
        out.toString(Charsets.UTF_8.name()).takeIf { it.isNotBlank() }
    }.getOrNull()

    /**
     * An ed25519 key rebuilt from the blob JSch would hand to an SSH agent, which is
     * `string type, string publicKey, string privateKey, string comment` — the private string
     * being the 32-byte seed followed by the public key.
     */
    private fun fromAgentBlob(kp: KeyPair): String? = runCatching {
        val blob = kp.forSSHAgent()
        val reader = SshStringReader(blob)
        if (reader.next()?.toString(Charsets.UTF_8) != "ssh-ed25519") return@runCatching null
        val publicKey = reader.next() ?: return@runCatching null
        val privateKey = reader.next() ?: return@runCatching null
        if (privateKey.size < 32) return@runCatching null
        val seed = privateKey.copyOfRange(0, 32)
        val params = org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(seed, 0)
        // Cheap self-check before the caller's: the seed has to belong to this key.
        if (!params.generatePublicKey().encoded.contentEquals(publicKey)) return@runCatching null
        val encoded = org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil.encodePrivateKey(params)
        val base64 = java.util.Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(encoded)
        "-----BEGIN OPENSSH PRIVATE KEY-----\n$base64\n-----END OPENSSH PRIVATE KEY-----\n"
    }.getOrNull()

    /** Walks the `uint32 length, bytes` strings an SSH blob is made of. */
    private class SshStringReader(private val blob: ByteArray) {
        private var at = 0

        fun next(): ByteArray? {
            if (at + 4 > blob.size) return null
            var length = 0
            repeat(4) { length = (length shl 8) or (blob[at++].toInt() and 0xff) }
            if (length < 0 || at + length > blob.size) return null
            return blob.copyOfRange(at, at + length).also { at += length }
        }
    }

    /**
     * Whether [pem] is an encrypted private key, and therefore useless without its passphrase.
     * A key this cannot parse at all counts as not encrypted: the failure to report then is
     * "unreadable key", which the import path already words for itself.
     */
    fun isKeyEncrypted(pem: String): Boolean = runCatching {
        val kp = KeyPair.load(JSch(), pem.replace("\r\n", "\n").trim().toByteArray(Charsets.UTF_8), null)
        try { kp.isEncrypted } finally { kp.dispose() }
    }.getOrDefault(false)

    /** Builds a known_hosts line from a JSch HostKey. */
    fun buildKnownHostsLine(hostKey: HostKey): String =
        "${hostKey.host} ${hostKey.type} ${hostKey.getKey()}"

    private fun applyLegacyCiphers(config: Properties) {
        val legacyCiphers = "aes128-cbc,aes192-cbc,aes256-cbc,3des-cbc"
        val legacyKex     = "diffie-hellman-group14-sha1,diffie-hellman-group-exchange-sha1,diffie-hellman-group1-sha1"
        val legacyHostKey = "ssh-dss"
        config.setProperty("cipher.s2c", "${JSch.getConfig("cipher.s2c")},$legacyCiphers")
        config.setProperty("cipher.c2s", "${JSch.getConfig("cipher.c2s")},$legacyCiphers")
        config.setProperty("kex",              "${JSch.getConfig("kex")},$legacyKex")
        config.setProperty("server_host_key",  "${JSch.getConfig("server_host_key")},$legacyHostKey")
    }
}

/**
 * The outcome of [SshManager.importPrivateKey]: the key text to store, which never needs a
 * passphrase, its public half, and its type.
 */
data class ImportedKey(
    val pem: String,
    val publicKey: String,
    val keyType: String,
)

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
     * Simple-mode: new host-key lines for jump hops with no prior stored key.
     * Persist these to the target host's jumpHostKeys field.
     */
    val newJumpHostKeyLines: List<String> = emptyList(),
    /**
     * Host-list-mode: (hostId, keyLine) pairs for jump hops with no prior stored key.
     * Persist each keyLine to the corresponding HostEntity's knownHostsEntry.
     */
    val newJumpHostKeyUpdates: List<Pair<Long, String>> = emptyList(),
) {
    val inputStream: java.io.InputStream get() = channelInput
    val isConnected get() = channel.isConnected && session.isConnected
    val exitStatus get() = channel.exitStatus

    // JSch's channel OutputStream (channel.outputStream) is NOT thread-safe: its write/flush
    // are unsynchronized and accumulate into a shared packet buffer. We write to it from
    // several threads (each sendInput coroutine, plus the reader thread's terminal-response
    // callback), so concurrent writes could interleave into one corrupt packet and drop the
    // connection. Serialize every write through this lock.
    private val writeLock = Any()

    /** Writes [data] to the remote shell's stdin and flushes, serialized against other writers. */
    fun write(data: ByteArray) {
        synchronized(writeLock) {
            try {
                stdinOutput.write(data)
                stdinOutput.flush()
                if (BuildConfig.DEBUG) SshDiagnostics.event("write ${data.size}b")
            } catch (e: Exception) {
                SshDiagnostics.onWriteError(e)   // debug-only; record before it propagates
                throw e
            }
        }
    }

    fun resize(columns: Int, rows: Int) {
        channel.setPtySize(columns, rows, columns * 8, rows * 16)
    }

    /**
     * Opens an SFTP channel on this connection, already authenticated and host-key checked —
     * for a quick side read (the shell history) without logging in a second time. The caller
     * owns the channel and must disconnect it; it runs alongside the shell, not in place of it.
     */
    fun openSftpChannel(timeoutMs: Int = 10_000): com.jcraft.jsch.ChannelSftp =
        (session.openChannel("sftp") as com.jcraft.jsch.ChannelSftp).also { it.connect(timeoutMs) }

    fun disconnect() {
        if (BuildConfig.DEBUG) SshDiagnostics.event("app disconnect() [shell]")
        runCatching { channel.disconnect() }
        runCatching { session.disconnect() }
        jumpSessions.reversed().forEach { runCatching { it.disconnect() } }
    }
}

/** A single entry returned by [SftpSession.listDir]. */
data class SftpEntry(
    val name: String,
    val isDir: Boolean,
    val isLink: Boolean,       // true if this is a symbolic link
    val size: Long,
    val modTimeSeconds: Int,   // Unix timestamp
)

/** Thrown by [SftpSession.readFile] when the file is larger than the caller is willing to hold. */
class FileTooLargeException(val size: Long, val limit: Long) :
    Exception("$size bytes exceeds the $limit byte limit")

/** A live SFTP session. */
class SftpSession(
    private val session: Session,
    channel: com.jcraft.jsch.ChannelSftp,
    val hostname: String,
    val hostKeyLine: String,
    private val jumpSessions: List<Session> = emptyList(),
    /** See [ShellSession.newJumpHostKeyLines]. */
    val newJumpHostKeyLines: List<String> = emptyList(),
    /** See [ShellSession.newJumpHostKeyUpdates]. */
    val newJumpHostKeyUpdates: List<Pair<Long, String>> = emptyList(),
    /** The working directory at the time the channel was opened (i.e. the user's home). */
    val homePath: String = "/",
) {
    /**
     * The foreground channel. Replaced by [recycle] when it can no longer be trusted — see there.
     */
    @Volatile private var channel: com.jcraft.jsch.ChannelSftp = channel

    val isConnected get() = channel.isConnected && session.isConnected

    /**
     * Runs [block] on the channel; if it fails in a way that can leave the channel out of step,
     * swaps in a fresh channel before rethrowing.
     *
     * SFTP matches replies to requests by reading them in order from one stream. A transfer
     * sends many writes ahead of their acknowledgements, so when it fails midway those
     * acknowledgements are still on their way; the next request then reads a stale reply as its
     * own, takes a length out of the middle of it and fails with an IndexOutOfBoundsException
     * or a corrupt packet — and so does every request after it. A plain status from the server
     * (no such file, permission denied) is a complete reply and leaves the stream in step; any
     * other failure — local I/O, a wrapped exception, a reply of the wrong type (JSch's
     * SSH_FX_FAILURE with no message), anything during a transfer — does not.
     *
     * Operations take turns: a channel serves one request/reply exchange at a time, and two
     * threads in it at once interleave their packets into the same buffer — the server then
     * reads garbage (an unknown message type, a bad MAC) and the channel or the whole
     * connection dies. A caller that finds the channel busy waits for it; debug builds log who
     * held it, so the path that overlapped can be found.
     */
    private inline fun <T> op(transfer: Boolean = false, block: (com.jcraft.jsch.ChannelSftp) -> T): T {
        if (!opLock.tryLock()) {
            if (BuildConfig.DEBUG) logContention()
            opLock.lock()
        }
        holder = Thread.currentThread()
        try {
            val ch = channel
            try {
                return block(ch)
            } catch (e: Exception) {
                val inStep = !transfer && e is com.jcraft.jsch.SftpException && e.cause == null &&
                    !(e.id == com.jcraft.jsch.ChannelSftp.SSH_FX_FAILURE && e.message.isNullOrEmpty())
                if (!inStep) recycle(ch)
                throw e
            }
        } finally {
            if (opLock.holdCount == 1) holder = null
            opLock.unlock()
        }
    }

    private val opLock = java.util.concurrent.locks.ReentrantLock()
    @Volatile private var holder: Thread? = null

    private fun logContention() {
        val busy = holder ?: return
        val here = Throwable("SFTP channel busy: waiting").stackTraceToString()
        val there = Throwable("…held by ${busy.name}").apply { stackTrace = busy.stackTrace }.stackTraceToString()
        android.util.Log.w("SftpSession", "$here\n$there")
    }

    /** Replaces [broken] with a new channel on the same session, if it is still the current one. */
    private fun recycle(broken: com.jcraft.jsch.ChannelSftp) = synchronized(this) {
        if (channel !== broken) return@synchronized
        runCatching { broken.disconnect() }
        if (!session.isConnected) return@synchronized
        runCatching {
            channel = (session.openChannel("sftp") as com.jcraft.jsch.ChannelSftp).also { it.connect(10_000) }
        }
    }

    /** Lists [path], returning entries sorted: dirs first, then files, both alphabetically. */
    @Suppress("UNCHECKED_CAST")
    fun listDir(path: String): List<SftpEntry> {
        val base = path.trimEnd('/')
        val raw = op { it.ls(path) } as Collection<com.jcraft.jsch.ChannelSftp.LsEntry>
        return raw
            .filter { it.filename != "." && it.filename != ".." }
            .map { e ->
                // lstat returns the symlink's own attrs, not the target's.
                // For symlinks we call stat() so isDir correctly reflects the target type.
                val isLink = e.attrs.isLink
                val isDir = if (isLink)
                    runCatching { op { it.stat("$base/${e.filename}") }.isDir }.getOrDefault(false)
                else
                    e.attrs.isDir
                SftpEntry(
                    name           = e.filename,
                    isDir          = isDir,
                    isLink         = isLink,
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
        op(transfer = true) { it.get(remotePath, dest, object : com.jcraft.jsch.SftpProgressMonitor {
            override fun init(op: Int, src: String?, dest: String?, max: Long) {}
            override fun count(count: Long): Boolean { received += count; onProgress(received); return true }
            override fun end() {}
        }) }
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
        op(transfer = true) { it.put(src, remotePath, object : com.jcraft.jsch.SftpProgressMonitor {
            override fun init(op: Int, src: String?, dest: String?, max: Long) {}
            override fun count(count: Long): Boolean { sent += count; onProgress(sent); return true }
            override fun end() {}
        }, com.jcraft.jsch.ChannelSftp.OVERWRITE) }
    }

    /** The size of [remotePath] in bytes. */
    fun sizeOf(remotePath: String): Long = op { it.stat(remotePath) }.size

    /**
     * Reads [remotePath] whole into memory, for the editor. Checks the size first and refuses
     * anything over [limit]: a file too big to edit is also too big to be worth downloading.
     */
    fun readFile(
        remotePath: String,
        limit: Long,
        onProgress: (bytesReceived: Long) -> Unit = {},
    ): ByteArray {
        val size = op { it.stat(remotePath) }.size
        if (size > limit) throw FileTooLargeException(size, limit)
        val out = java.io.ByteArrayOutputStream(size.coerceIn(0L, limit).toInt())
        downloadFile(remotePath, out, onProgress)
        return out.toByteArray()
    }

    /**
     * Replaces [remotePath]'s contents with [bytes] without ever leaving it half-written: the
     * new contents go to a hidden sibling first and take the original's place only once they
     * are all there, so a connection lost mid-write costs the temporary file and nothing else.
     *
     * The swap is a plain rename, which JSch turns into posix-rename@openssh.com wherever the
     * server offers it (OpenSSH always does) — one atomic step, and the original survives if it
     * fails. Servers with only the SFTP v3 rename refuse to overwrite an existing name; there
     * we write in place instead rather than delete the original and hope.
     */
    fun writeFile(remotePath: String, bytes: ByteArray) {
        val slash = remotePath.lastIndexOf('/')
        val dir = if (slash <= 0) "" else remotePath.substring(0, slash)
        val name = remotePath.substring(slash + 1)
        val temp = "$dir/.$name.sshborg-tmp"
        val mode = runCatching { op { it.stat(remotePath) }.permissions }.getOrNull()

        try {
            uploadFile(java.io.ByteArrayInputStream(bytes), temp)
        } catch (e: Exception) {
            runCatching { op { it.rm(temp) } }
            throw e
        }
        // The temporary file is created with the server's default mode; give it the original's
        // before the swap, or saving would quietly clear an executable bit or widen a 0600 file.
        if (mode != null) runCatching { op { it.chmod(mode, temp) } }
        try {
            op { it.rename(temp, remotePath) }
        } catch (e: com.jcraft.jsch.SftpException) {
            android.util.Log.w("SftpSession", "rename onto an existing name refused, writing in place", e)
            uploadFile(java.io.ByteArrayInputStream(bytes), remotePath)
            runCatching { op { it.rm(temp) } }
        }
    }

    fun deleteFile(remotePath: String) = op { it.rm(remotePath) }
    fun deleteDir(remotePath: String)  = op { it.rmdir(remotePath) }

    fun rename(oldPath: String, newPath: String) = op { it.rename(oldPath, newPath) }
    fun mkdir(remotePath: String)      = op { it.mkdir(remotePath) }

    /** Opens a second SFTP channel on the same authenticated session for background transfers. */
    fun openBackgroundChannel(): com.jcraft.jsch.ChannelSftp {
        val ch = session.openChannel("sftp") as com.jcraft.jsch.ChannelSftp
        ch.connect()
        return ch
    }

    fun disconnect() {
        runCatching { channel.disconnect() }
        runCatching { session.disconnect() }
        jumpSessions.reversed().forEach { runCatching { it.disconnect() } }
    }
}
