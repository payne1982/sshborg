package com.sshborg.ui.terminal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sshborg.SshBorgApp
import com.sshborg.data.db.HostEntity
import com.sshborg.data.ssh.*
import com.sshborg.terminal.TerminalEmulator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed interface ConnectionState {
    object Connecting : ConnectionState
    object Connected : ConnectionState
    data class HostKeyPrompt(val hostname: String, val fingerprint: String) : ConnectionState
    data class PasswordPrompt(val hostname: String) : ConnectionState
    data class Error(val message: String) : ConnectionState
    object Disconnected : ConnectionState
}

class TerminalViewModel(app: Application) : AndroidViewModel(app) {

    private val sshBorgApp = app as SshBorgApp
    private val hostDao = sshBorgApp.db.hostDao()
    private val keyDao  = sshBorgApp.db.sshKeyDao()

    val emulator = TerminalEmulator(80, 24)

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
    val state: StateFlow<ConnectionState> = _state

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title

    // Riferimento diretto alla view — settato da TerminalScreen nell'update block
    var terminalViewRef: com.sshborg.terminal.TerminalView? = null

    // Chiamato dal reader loop (thread IO) per chiedere alla view di ridisegnarsi.
    // postInvalidate() è thread-safe, a differenza di invalidate().
    var onNeedsRedraw: (() -> Unit)? = null

    private var shellSession: ShellSession? = null
    private var readerJob: Job? = null
    private var connectJob: Job? = null

    // Deferred results for dialogs
    private val hostKeyResult = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    private val passwordResult = MutableSharedFlow<String>(extraBufferCapacity = 1)

    fun connect(hostId: Long, columns: Int = 80, rows: Int = 24) {
        // Prevent multiple concurrent connection attempts
        if (connectJob?.isActive == true || shellSession?.isConnected == true) return
        emulator.onTitleChanged = { t -> _title.value = t }

        connectJob = viewModelScope.launch(Dispatchers.IO) {
            val host = hostDao.getById(hostId) ?: run {
                _state.value = ConnectionState.Error("Host not found")
                return@launch
            }

            val auth: SshAuth = buildAuth(host) ?: return@launch

            _state.value = ConnectionState.Connecting

            val params = SshConnectionParams(
                hostname = host.hostname,
                port = host.port,
                username = host.username,
                auth = auth,
                agentForwarding = host.agentForwarding,
                knownHostsEntry = host.knownHostsEntry,
            )

            runCatching {
                SshManager.openShell(
                    params = params,
                    columns = columns,
                    rows = rows,
                    onHostKeyVerify = { hostname: String, fingerprint: String ->
                        runBlocking {
                            _state.value = ConnectionState.HostKeyPrompt(hostname, fingerprint)
                            hostKeyResult.first()
                        }
                    },
                )
            }.onSuccess { session ->
                shellSession = session
                // Sync PTY with the actual screen size. onSizeChanged() may have
                // resized the emulator while the connection was still establishing,
                // but shellSession was null at that point so the resize was not sent.
                synchronized(emulator) { session.resize(emulator.buffer.columns, emulator.buffer.rows) }
                _state.value = ConnectionState.Connected
                // Persist host key if it's a first-time connection
                if (host.knownHostsEntry == null) {
                    hostDao.upsert(host.copy(knownHostsEntry = session.hostKeyLine))
                }
                hostDao.updateLastConnected(hostId, System.currentTimeMillis())
                startReading(session)
            }.onFailure { err ->
                _state.value = ConnectionState.Error(err.message ?: "Connection failed")
            }
        }
    }

    private suspend fun buildAuth(host: HostEntity): SshAuth? {
        val keyPem = host.keyId?.let { keyDao.getById(it)?.privateKeyPem }
        return if (host.keyId != null && keyPem != null) {
            SshAuth.PublicKey(keyPem)
        } else {
            _state.value = ConnectionState.PasswordPrompt(host.hostname)
            val pwd = passwordResult.first()
            if (pwd.isEmpty()) { _state.value = ConnectionState.Disconnected; null }
            else SshAuth.Password(pwd)
        }
    }

    private fun startReading(session: ShellSession) {
        // Hook emulator responses (CPR, DA) directly to the SSH output stream
        emulator.onSendResponse = { bytes ->
            runCatching { session.outputStream.write(bytes); session.outputStream.flush() }
        }
        readerJob = viewModelScope.launch(Dispatchers.IO) {
            val buf = ByteArray(4096)
            try {
                while (isActive && session.isConnected) {
                    val n = session.inputStream.read(buf)
                    if (n < 0) break
                    synchronized(emulator) { emulator.process(buf, 0, n) }
                    onNeedsRedraw?.invoke()
                }
            } catch (_: Exception) {}
            _state.value = ConnectionState.Disconnected
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
        synchronized(emulator) { emulator.resize(cols, rows) }
        // channel.setPtySize() must run on the IO thread — calling it from the UI thread
        // while the reader job is active causes concurrent JSch session access and corrupts
        // internal state, which surfaces as a spurious disconnect after the next keypress.
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { shellSession?.resize(cols, rows) }
        }
    }

    fun acceptHostKey() { hostKeyResult.tryEmit(true) }
    fun rejectHostKey() { hostKeyResult.tryEmit(false) }
    fun submitPassword(password: String) { passwordResult.tryEmit(password) }

    fun disconnect() {
        connectJob?.cancel()
        readerJob?.cancel()
        shellSession?.disconnect()
        shellSession = null
        _state.value = ConnectionState.Disconnected
    }

    override fun onCleared() { super.onCleared(); disconnect() }
}
