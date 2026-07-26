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
import java.io.ByteArrayOutputStream

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
    private val prefs          = sshBorgApp.appPreferences

    private var scrollbackLines = 2000
    init {
        viewModelScope.launch {
            scrollbackLines = prefs.scrollbackLines.first()
        }
    }

    private var sessionId: String? = null

    /** Emulator for the current session — initialized in attach(). */
    private val _emulator = MutableStateFlow(TerminalEmulator(80, 24))
    val emulatorFlow: StateFlow<TerminalEmulator> = _emulator.asStateFlow()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
    val state: StateFlow<ConnectionState> = _state

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title

    var onNeedsRedraw: (() -> Unit)? = null

    private var shellSession: ShellSession? = null
    private var readerJob: Job? = null
    private var connectJob: Job? = null

    private val hostKeyResult  = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    private val passwordResult = MutableSharedFlow<String>(extraBufferCapacity = 1)

    // ── Command history suggestions ───────────────────────────────────────────

    /** Commands loaded from the server's shell history file, most-recent first. */
    private val _commandHistory = MutableStateFlow<List<String>>(emptyList())

    /** Filtered suggestions for the current partial input. */
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions

    val suggestionsBarSticky: StateFlow<Boolean> =
        prefs.suggestionsBarSticky.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Prompt string detected from first terminal render, used to strip it from the input line. */
    private var promptPrefix = ""
    private var promptDetected = false
    private var promptDetectJob: Job? = null
    private var suggestionsUpdateJob: Job? = null

    /** Params saved after a successful connect, used to open the SFTP history channel. */
    private var lastConnectParams: SshConnectionParams? = null

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
        val em = session.emulator ?: TerminalEmulator(80, 24, scrollbackLines).also { newEm ->
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

            // Pre-load jump host entities for host-list mode so we can look them up in onHostKeyVerify
            val jumpHostEntities = if (host.jumpMode == "host_list") {
                buildJumpHostEntities(host.jumpHostIdList)
            } else emptyList()

            while (true) {
                _state.value = ConnectionState.Connecting

                val jumpHosts = if (host.jumpMode == "host_list") {
                    jumpHostEntities.mapNotNull { jumpHost ->
                        val jumpAuth = buildJumpAuth(jumpHost) ?: return@mapNotNull null
                        JumpHost(
                            host             = jumpHost.hostname,
                            port             = jumpHost.port,
                            username         = jumpHost.username,
                            knownHostsEntry  = jumpHost.knownHostsEntry,
                            auth             = jumpAuth,
                            hostId           = jumpHost.id,
                        )
                    }
                } else {
                    parseJumpHosts(host.jumpHosts, host.jumpHostKeys)
                }

                val result = runCatching {
                    SshManager.openShell(
                        params = SshConnectionParams(
                            hostname           = host.hostname,
                            port               = host.port,
                            username           = host.username,
                            auth               = auth,
                            agentForwarding    = host.agentForwarding,
                            knownHostsEntry    = host.knownHostsEntry,
                            jumpHosts          = jumpHosts,
                            portForwardings    = parsePortForwardings(host.portForwardings),
                            allowLegacyCiphers = host.allowLegacyCiphers,
                        ),
                        columns = columns,
                        rows    = rows,
                        onHostKeyVerify = { hostname, fingerprint, keyLine ->
                            runBlocking {
                                _state.value = ConnectionState.HostKeyPrompt(hostname, fingerprint)
                                val accepted = hostKeyResult.first()
                                if (accepted) {
                                    _state.value = ConnectionState.Connecting
                                    val current = hostDao.getById(hostId)
                                    if (current != null) {
                                        if (hostname == host.hostname) {
                                            if (current.knownHostsEntry == null)
                                                hostDao.upsert(current.copy(knownHostsEntry = keyLine))
                                        } else if (host.jumpMode == "host_list") {
                                            // Host-list: persist key to the jump host's own entity
                                            val jumpEntity = jumpHostEntities.find { it.hostname == hostname }
                                            if (jumpEntity != null) {
                                                val jCurrent = hostDao.getById(jumpEntity.id)
                                                if (jCurrent != null && jCurrent.knownHostsEntry == null)
                                                    hostDao.upsert(jCurrent.copy(knownHostsEntry = keyLine))
                                            }
                                        } else {
                                            // Simple mode: persist to target's jumpHostKeys blob
                                            val existing = current.jumpHostKeys
                                                ?.lines()?.filter { it.isNotBlank() } ?: emptyList()
                                            if (keyLine !in existing)
                                                hostDao.upsert(current.copy(jumpHostKeys = (existing + keyLine).joinToString("\n")))
                                        }
                                    }
                                }
                                accepted
                            }
                        },
                    )
                }

                val session = result.getOrNull()
                if (session != null) {
                    shellSession = session
                    val em = _emulator.value
                    synchronized(em) { session.resize(em.buffer.columns, em.buffer.rows) }
                    withContext(Dispatchers.Main.immediate) {
                        sessionManager.update(id) { it.copy(shellSession = session, status = SessionManager.Status.Connected) }
                        _state.value = ConnectionState.Connected
                    }
                    val saved = hostDao.getById(hostId) ?: host
                    if (saved.knownHostsEntry == null)
                        hostDao.upsert(saved.copy(knownHostsEntry = session.hostKeyLine))
                    // Simple mode: new jump keys → target's jumpHostKeys blob
                    if (session.newJumpHostKeyLines.isNotEmpty()) {
                        val current = hostDao.getById(hostId) ?: saved
                        val existing = current.jumpHostKeys?.lines()?.filter { it.isNotBlank() } ?: emptyList()
                        val toAdd = session.newJumpHostKeyLines.filter { it !in existing }
                        if (toAdd.isNotEmpty())
                            hostDao.upsert(current.copy(jumpHostKeys = (existing + toAdd).joinToString("\n")))
                    }
                    // Host-list mode: new jump keys → each jump host's own knownHostsEntry
                    for ((jumpHostId, keyLine) in session.newJumpHostKeyUpdates) {
                        val jCurrent = hostDao.getById(jumpHostId) ?: continue
                        if (jCurrent.knownHostsEntry == null)
                            hostDao.upsert(jCurrent.copy(knownHostsEntry = keyLine))
                    }
                    hostDao.updateLastConnected(hostId, System.currentTimeMillis())
                    lastConnectParams = SshConnectionParams(
                        hostname           = host.hostname,
                        port               = host.port,
                        username           = host.username,
                        auth               = auth,
                        agentForwarding    = host.agentForwarding,
                        knownHostsEntry    = session.hostKeyLine,
                        jumpHosts          = jumpHosts,
                        allowLegacyCiphers = host.allowLegacyCiphers,
                    )
                    startReading(session)
                    if (prefs.historySuggestions.first()) loadCommandHistory()
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
                    _state.value = ConnectionState.Error(err?.message?.takeIf { it.isNotBlank() } ?: getApplication<Application>().getString(R.string.error_connection_failed))
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

    private suspend fun buildJumpHostEntities(idList: String?): List<HostEntity> {
        if (idList.isNullOrBlank()) return emptyList()
        return idList.split(",").mapNotNull { it.trim().toLongOrNull() }
            .mapNotNull { hostDao.getById(it) }
    }

    /** Non-interactive auth for jump hosts — password must already be saved. */
    private suspend fun buildJumpAuth(host: HostEntity): SshAuth? {
        val keyPem = host.keyId?.let { id -> keyDao.getById(id)?.let { KeystoreManager.getPrivateKeyPem(it) } }
        return when {
            host.keyId != null && keyPem != null -> SshAuth.PublicKey(keyPem)
            !host.encryptedPassword.isNullOrEmpty() ->
                runCatching { SshAuth.Password(KeystoreManager.decrypt(host.encryptedPassword)) }.getOrNull()
            !host.password.isNullOrEmpty() -> SshAuth.Password(host.password)
            else -> null
        }
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
                    if (n < 0) {
                        var waited = 0
                        while (session.exitStatus == -1 && waited < 1000) {
                            kotlinx.coroutines.delay(50)
                            waited += 50
                        }
                        if (session.exitStatus != -1) {
                            cleanExit = true
                        } else {
                            disconnectCause = getApplication<Application>().getString(R.string.terminal_connection_lost)
                        }
                        break
                    }
                    synchronized(em) { em.process(buf, 0, n) }
                    onNeedsRedraw?.invoke()
                    detectPromptIfNeeded()
                    scheduleUpdateSuggestions()
                }
                // Loop exited because session.isConnected flipped without EOF — unexpected disconnect.
                if (isActive && !cleanExit && disconnectCause == null) {
                    if (session.exitStatus != -1) {
                        cleanExit = true
                    } else {
                        disconnectCause = getApplication<Application>().getString(R.string.terminal_connection_lost)
                    }
                }
            } catch (e: Exception) {
                // JSch may close the pipe via disconnect() before or instead of returning EOF,
                // causing read() to throw IOException even on a clean exit. If exit-status is
                // already set, treat it as a clean exit rather than an error.
                if (session.exitStatus != -1) {
                    cleanExit = true
                } else {
                    disconnectCause = if (BuildConfig.DEBUG) e.toString()
                                      else "${e.javaClass.simpleName}${e.message?.let { ": $it" } ?: ""}"
                }
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
                    _navBack.tryEmit(Unit)
                } else {
                    _state.value = ConnectionState.Disconnected(disconnectCause)
                }
            }
        }
    }

    fun sendInput(data: ByteArray) {
        // Enter (\r or \n) and Ctrl+C immediately clear suggestions — the line is gone.
        if (data.size == 1 && (data[0] == 0x0D.toByte() || data[0] == 0x0A.toByte() || data[0] == 0x03.toByte())) {
            _suggestions.value = emptyList()
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                shellSession?.outputStream?.write(data)
                shellSession?.outputStream?.flush()
            }
        }
    }

    /** Returns the correct escape sequence for a cursor key, respecting DECCKM mode. */
    fun cursorKeyBytes(code: Char): ByteArray {
        val appMode = _emulator.value.applicationCursorKeys
        return if (appMode) "\u001bO$code".toByteArray() else "\u001b[$code".toByteArray()
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

    // ── History suggestions helpers ───────────────────────────────────────────

    /**
     * Opens a brief SFTP connection and tries to read the shell history file.
     * Tries ~/.bash_history, ~/.zsh_history, and fish history in order.
     * Silently does nothing if the connection or file read fails.
     */
    private fun loadCommandHistory() {
        val params = lastConnectParams ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val sftp = runCatching {
                SshManager.openSftp(params) { _, _, _ -> true }
            }.getOrNull() ?: return@launch
            try {
                val home = sftp.homePath
                val candidates = listOf(
                    "$home/.bash_history",
                    "$home/.zsh_history",
                    "$home/.local/share/fish/fish_history",
                )
                for (path in candidates) {
                    val content = runCatching {
                        val out = ByteArrayOutputStream()
                        sftp.downloadFile(path, out) {}
                        out.toString(Charsets.UTF_8.name())
                    }.getOrNull() ?: continue
                    val commands = parseHistory(path, content)
                    if (commands.isNotEmpty()) {
                        _commandHistory.value = commands
                            .reversed()
                            .filter { it.length > 1 }
                            .distinct()
                            .take(100_000)
                        break
                    }
                }
            } finally {
                runCatching { sftp.disconnect() }
            }
        }
    }

    private fun parseHistory(path: String, content: String): List<String> {
        val lines = content.lines()
        return when {
            path.endsWith("fish_history") ->
                // fish format: "- cmd: git status\n  when: 12345"
                lines.filter { it.startsWith("- cmd: ") }
                     .map { it.removePrefix("- cmd: ").trim() }
            path.endsWith("zsh_history") ->
                // Extended format: ": 1234567890:0;command" — or plain one-per-line
                lines.map { line ->
                    Regex("""^: \d+:\d+;(.*)$""").matchEntire(line)?.groupValues?.get(1) ?: line
                }.filter { it.isNotBlank() }
            else ->
                // bash_history: one command per line; timestamp lines start with '#'
                lines.filter { it.isNotBlank() && !it.startsWith('#') }
        }
    }

    /**
     * Schedules a prompt detection after a short debounce.
     * Runs only until the prompt is detected for the first time.
     */
    private fun detectPromptIfNeeded() {
        if (promptDetected) return
        promptDetectJob?.cancel()
        promptDetectJob = viewModelScope.launch {
            delay(300)
            val em = _emulator.value
            val lineText = synchronized(em) {
                em.buffer.getRowText(em.buffer.cursorRow).trimEnd()
            }
            if (lineText.isEmpty()) return@launch
            // Match everything up to and including a prompt marker ($, #, %, ❯, >) followed by a space
            val m = Regex("""^(.*?[\$#%❯>])\s""").find(lineText) ?: return@launch
            promptPrefix = m.groupValues[1] + " "
            promptDetected = true
        }
    }

    /**
     * Returns the portion of the current terminal line that the user is typing,
     * with the shell prompt stripped. Used for suggestion filtering.
     */
    fun getCurrentInputForCompletion(): String {
        if (!promptDetected || promptPrefix.isEmpty()) return ""
        val em = _emulator.value
        val lineText = synchronized(em) {
            em.buffer.getRowText(em.buffer.cursorRow, em.buffer.cursorCol)
        }
        if (lineText.startsWith(promptPrefix)) return lineText.removePrefix(promptPrefix)
        // Prompt changed (e.g. after cd) — heuristic: text after last prompt marker
        // Return "" rather than the raw line to avoid false positives
        val m = Regex("""[\$#%❯>]\s(.*)$""").find(lineText)
        return m?.groupValues?.get(1) ?: ""
    }

    /** Debounced update of the suggestions list based on current terminal input. */
    private fun scheduleUpdateSuggestions() {
        if (!promptDetected) return
        suggestionsUpdateJob?.cancel()
        suggestionsUpdateJob = viewModelScope.launch {
            delay(80)
            val history = _commandHistory.value
            if (history.isEmpty()) return@launch
            val input = getCurrentInputForCompletion()
            _suggestions.value = if (input.length < 2) emptyList()
            else history.filter { it.startsWith(input) && it != input }.take(8)
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
