package com.sshborg.ui.terminal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.sshborg.R
import androidx.lifecycle.viewModelScope
import com.sshborg.BuildConfig
import com.sshborg.SshBorgApp
import com.sshborg.data.db.HostEntity
import com.sshborg.data.KeystoreManager
import com.sshborg.data.ssh.*
import com.sshborg.service.SessionManager
import com.sshborg.service.SshForegroundService
import com.sshborg.terminal.TerminalEmulator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed interface ConnectionState {
    object Connecting : ConnectionState
    object Connected : ConnectionState
    data class HostKeyPrompt(val hostname: String, val fingerprint: String) : ConnectionState
    data class PasswordPrompt(val hostname: String, val wrongPassword: Boolean = false) : ConnectionState
    data class Error(val message: String) : ConnectionState
    data class Disconnected(val cause: String? = null) : ConnectionState
}

class TerminalViewModel(app: Application) : AndroidViewModel(app) {

    private val sshBorgApp    = app as SshBorgApp
    private val sessionManager = sshBorgApp.sessionManager
    private val hostDao        = sshBorgApp.db.hostDao()
    private val keyDao         = sshBorgApp.db.sshKeyDao()

    private var sessionId: String? = null

    /** Emulator for the current session — initialized in attach(). */
    private val _emulator = MutableStateFlow(TerminalEmulator(80, 24))
    val emulatorFlow: StateFlow<TerminalEmulator> = _emulator.asStateFlow()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
    val state: StateFlow<ConnectionState> = _state

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title

    var terminalViewRef: com.sshborg.terminal.TerminalView? = null
    var onNeedsRedraw: (() -> Unit)? = null

    private var shellSession: ShellSession? = null
    private var readerJob: Job? = null
    private var connectJob: Job? = null

    private val hostKeyResult  = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    private val passwordResult = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Emitted when the remote shell exits cleanly — screen should navigate back automatically. */
    private val _navBack = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navBack: SharedFlow<Unit> = _navBack

    /**
     * Attaches this ViewModel to an existing session in [SessionManager].
     * Must be called before [connect]. If the session is already connected, starts reading.
     */
    fun attach(id: String) {
        sessionId = id
        val session = sessionManager.get(id) ?: run {
            _state.value = ConnectionState.Error(getApplication<Application>().getString(R.string.error_session_not_found))
            return
        }

        // Get or create the emulator for this session
        val em = session.emulator ?: TerminalEmulator(80, 24).also { newEm ->
            sessionManager.update(id) { it.copy(emulator = newEm) }
        }
        em.onTitleChanged = { t -> _title.value = t }
        _emulator.value = em

        // If already connected, resume reading
        if (session.shellSession != null) {
            shellSession = session.shellSession
            if (session.shellSession.isConnected) {
                _state.value = ConnectionState.Connected
                startReading(session.shellSession)
            } else {
                _state.value = ConnectionState.Disconnected()
                sessionManager.update(id) { it.copy(status = SessionManager.Status.Disconnected) }
            }
        }
        // else: new session, connect() will be called next
    }

    /** Starts the SSH connection for a newly created session. */
    fun connect(columns: Int = 80, rows: Int = 24) {
        val id     = sessionId ?: return
        val hostId = sessionManager.get(id)?.hostId ?: return
        if (connectJob?.isActive == true) return

        connectJob = viewModelScope.launch(Dispatchers.IO) {
            val host = hostDao.getById(hostId) ?: run {
                _state.value = ConnectionState.Error(getApplication<Application>().getString(R.string.error_host_not_found)); return@launch
            }
            var auth = buildAuth(host) ?: return@launch
            var wrongPassword = false

            while (true) {
                _state.value = ConnectionState.Connecting

                val result = runCatching {
                    SshManager.openShell(
                        params = SshConnectionParams(
                            hostname        = host.hostname,
                            port            = host.port,
                            username        = host.username,
                            auth            = auth,
                            agentForwarding = host.agentForwarding,
                            knownHostsEntry = host.knownHostsEntry,
                            jumpHosts       = parseJumpHosts(host.jumpHosts, host.jumpHostKeys),
                        ),
                        columns = columns,
                        rows    = rows,
                        onHostKeyVerify = { hostname, fingerprint ->
                            runBlocking {
                                _state.value = ConnectionState.HostKeyPrompt(hostname, fingerprint)
                                val accepted = hostKeyResult.first()
                                if (accepted) _state.value = ConnectionState.Connecting
                                accepted
                            }
                        },
                    )
                }

                val session = result.getOrNull()
                if (session != null) {
                    shellSession = session
                    sessionManager.update(id) { it.copy(shellSession = session, status = SessionManager.Status.Connected) }
                    val em = _emulator.value
                    synchronized(em) { session.resize(em.buffer.columns, em.buffer.rows) }
                    _state.value = ConnectionState.Connected
                    if (host.knownHostsEntry == null)
                        hostDao.upsert(host.copy(knownHostsEntry = session.hostKeyLine))
                    if (session.newJumpHostKeyLines.isNotEmpty()) {
                        val current = hostDao.getById(hostId) ?: host
                        val existing = current.jumpHostKeys?.lines()?.filter { it.isNotBlank() } ?: emptyList()
                        val merged = (existing + session.newJumpHostKeyLines).joinToString("\n")
                        hostDao.upsert(current.copy(jumpHostKeys = merged))
                    }
                    hostDao.updateLastConnected(hostId, System.currentTimeMillis())
                    startReading(session)
                    return@launch
                }

                val err = result.exceptionOrNull()
                if (isAuthFailure(err) && auth !is SshAuth.PublicKey) {
                    _state.value = ConnectionState.PasswordPrompt(host.hostname, wrongPassword = true)
                    val pwd = passwordResult.first()
                    if (pwd.isEmpty()) {
                        _state.value = ConnectionState.Disconnected()
                        sessionManager.remove(id)
                        if (sessionManager.sessions.value.isEmpty()) SshForegroundService.stop(getApplication())
                        return@launch
                    }
                    auth = SshAuth.Password(pwd)
                } else {
                    _state.value = ConnectionState.Error(err?.message ?: getApplication<Application>().getString(R.string.error_connection_failed))
                    sessionManager.remove(id)
                    if (sessionManager.sessions.value.isEmpty()) SshForegroundService.stop(getApplication())
                    return@launch
                }
            }
        }
    }

    private fun isAuthFailure(err: Throwable?): Boolean {
        val msg = err?.message ?: return false
        return msg.contains("Auth fail", ignoreCase = true) ||
               msg.contains("Auth cancel", ignoreCase = true) ||
               msg.contains("USERAUTH", ignoreCase = true) ||
               msg.contains("authentication", ignoreCase = true)
    }

    private suspend fun buildAuth(host: HostEntity): SshAuth? {
        val keyPem = host.keyId?.let { id -> keyDao.getById(id)?.let { KeystoreManager.getPrivateKeyPem(it) } }
        return if (host.keyId != null && keyPem != null) {
            SshAuth.PublicKey(keyPem)
        } else if (!host.encryptedPassword.isNullOrEmpty()) {
            SshAuth.Password(KeystoreManager.decrypt(host.encryptedPassword))
        } else if (!host.password.isNullOrEmpty()) {
            SshAuth.Password(host.password)
        } else {
            _state.value = ConnectionState.PasswordPrompt(host.hostname)
            val pwd = passwordResult.first()
            if (pwd.isEmpty()) { _state.value = ConnectionState.Disconnected(); null }
            else SshAuth.Password(pwd)
        }
    }

    private fun startReading(session: ShellSession) {
        val em = _emulator.value
        em.onSendResponse = { bytes ->
            runCatching { session.outputStream.write(bytes); session.outputStream.flush() }
        }
        readerJob?.cancel()
        readerJob = viewModelScope.launch(Dispatchers.IO) {
            val buf = ByteArray(4096)
            var cleanExit = false
            var disconnectCause: String? = null
            try {
                while (isActive && session.isConnected) {
                    val n = session.inputStream.read(buf)
                    if (n < 0) { cleanExit = true; break }
                    synchronized(em) { em.process(buf, 0, n) }
                    onNeedsRedraw?.invoke()
                }
                // Loop exited because session.isConnected flipped (e.g. server closed channel)
                if (isActive && !cleanExit) cleanExit = true
            } catch (e: Exception) {
                disconnectCause = if (BuildConfig.DEBUG) e.toString()
                                  else "${e.javaClass.simpleName}${e.message?.let { ": $it" } ?: ""}"
            }

            // If the job was cancelled (background() called), don't touch the session
            if (!isActive) return@launch

            // Session ended — remove it from SessionManager so the notification updates
            val id = sessionId ?: return@launch
            if (sessionManager.get(id)?.shellSession === session) {
                sessionManager.remove(id)
                if (sessionManager.sessions.value.isEmpty()) {
                    SshForegroundService.stop(getApplication())
                }
                if (cleanExit) {
                    // Shell exited normally (exit/logout) — go back automatically
                    _navBack.tryEmit(Unit)
                } else {
                    // Unexpected disconnect — show overlay so the user knows
                    _state.value = ConnectionState.Disconnected(disconnectCause)
                }
            }
        }
    }

    fun sendInput(data: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                shellSession?.outputStream?.write(data)
                shellSession?.outputStream?.flush()
            }
        }
    }

    fun resize(cols: Int, rows: Int) {
        val em = _emulator.value
        synchronized(em) { em.resize(cols, rows) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { shellSession?.resize(cols, rows) }
        }
    }

    fun acceptHostKey() { hostKeyResult.tryEmit(true) }
    fun rejectHostKey() { hostKeyResult.tryEmit(false) }
    fun submitPassword(password: String) { passwordResult.tryEmit(password) }

    /** Stops the reading loop but keeps the session alive in [SessionManager]. */
    fun background() {
        readerJob?.cancel()
        readerJob = null
        onNeedsRedraw = null
    }

    /** Fully disconnects and removes the session from [SessionManager]. */
    fun disconnect() {
        val id = sessionId ?: return
        connectJob?.cancel()
        readerJob?.cancel()
        shellSession = null
        sessionManager.remove(id)
        _state.value = ConnectionState.Disconnected()
        if (sessionManager.sessions.value.isEmpty()) {
            SshForegroundService.stop(getApplication())
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Stop reading loop — session stays alive in background
        background()
        // If the connection never completed, clean up the dangling session
        val id = sessionId ?: return
        if (sessionManager.get(id)?.status == SessionManager.Status.Connecting) {
            sessionManager.remove(id)
            if (sessionManager.sessions.value.isEmpty()) {
                SshForegroundService.stop(getApplication())
            }
        }
    }
}
