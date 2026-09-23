package com.sshborg.ui.sftp

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import com.sshborg.BuildConfig
import com.sshborg.R
import androidx.lifecycle.viewModelScope
import com.sshborg.SshBorgApp
import com.sshborg.data.db.HostEntity
import com.sshborg.data.KeystoreManager
import com.sshborg.data.ssh.*
import android.webkit.MimeTypeMap
import com.sshborg.service.BackgroundTransfer
import com.sshborg.service.DownloadIntents
import com.sshborg.service.FileFailure
import com.sshborg.service.SessionManager
import com.sshborg.service.SshForegroundService
import com.sshborg.service.TransferTask
import com.sshborg.ui.editor.EDITOR_MAX_BYTES
import com.sshborg.ui.editor.EDITOR_VIEW_MAX_BYTES
import com.sshborg.ui.editor.EDITOR_WARN_BYTES
import com.sshborg.ui.editor.EditorState
import com.sshborg.ui.editor.TextFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class SftpViewModel(app: Application) : AndroidViewModel(app) {

    sealed interface State {
        object Connecting : State
        object Preparing : State
        data class HostKeyPrompt(val hostname: String, val fingerprint: String) : State
        data class PasswordPrompt(val hostname: String, val wrongPassword: Boolean = false) : State
        data class Listing(val path: String, val entries: List<SftpEntry>, val nonce: Long = 0L) : State
        data class Downloading(val filename: String, val location: String, val bytesReceived: Long, val fileIndex: Int = 1, val totalFiles: Int = 1, val startedAt: Long = 0L) : State
        data class Downloaded(
            val filename: String,
            val location: String,
            val totalFiles: Int = 1,
            val skippedFiles: Int = 0,
            val startedAt: Long = 0L,
            val completedAt: Long = 0L,
            /** The skipped files' report, reopenable from the completion screen. */
            val report: ErrorReport? = null,
        ) : State
        data class Deleting(val name: String, val index: Int = 1, val total: Int = 1) : State
        data class Uploading(val filename: String, val bytesSent: Long, val fileIndex: Int = 1, val totalFiles: Int = 1) : State
        data class Error(val message: String, val detail: String? = null) : State
        object Disconnected : State
    }

    /**
     * Subfolder inside Downloads used for all downloads.
     * Debug builds get their own folder so they never conflict with the release app,
     * since Android scoped storage prevents cross-package file visibility/deletion.
     */
    val downloadFolder =
        "${Environment.DIRECTORY_DOWNLOADS}/SSHBorg${if (BuildConfig.DEBUG) "-debug" else ""}/"

    private val sshBorgApp      = app as SshBorgApp
    private val sessionManager  = sshBorgApp.sessionManager
    private val transferManager = sshBorgApp.transferManager
    private val hostDao         = sshBorgApp.db.hostDao()
    private val keyDao          = sshBorgApp.db.sshKeyDao()

    private var sessionId: String? = null

    private val _state = MutableStateFlow<State>(State.Connecting)
    val state: StateFlow<State> = _state

    /** Short notices (not errors) shown as a snackbar. */
    private val _opError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val opError: SharedFlow<String> = _opError

    /** The failure report on screen, if any; it stays until [dismissReport]. */
    private val _report = MutableStateFlow<ErrorReport?>(null)
    val report: StateFlow<ErrorReport?> = _report

    fun dismissReport() { _report.value = null }

    /** The file open in the editor, if any. See [openEditor]. */
    private val _editor = MutableStateFlow<EditorState?>(null)
    val editor: StateFlow<EditorState?> = _editor

    /**
     * The open file's bytes, kept as they arrived so another charset can be tried against them
     * without going back to the server — and so saving always starts from the original.
     */
    @Volatile private var editorBytes: ByteArray? = null
    @Volatile private var editorBytesPath: String? = null

    private fun hold(path: String, bytes: ByteArray) {
        editorBytes = bytes
        editorBytesPath = path
    }

    private fun heldBytes(path: String): ByteArray? =
        editorBytes?.takeIf { editorBytesPath == path }

    /**
     * Opens [remotePath]: reads it whole, then decides what to do with it.
     *
     * Reading first and asking afterwards, rather than the other way round, because every
     * answer needs the bytes anyway — the hex editor, the read-only view and the editor all do
     * — and because it is the only way to know what the file *is* without stopping a transfer
     * halfway, which is how a channel ends up out of step with the server. The one question
     * asked before reading is the one the size alone can answer: too big even to hold.
     */
    fun openEditor(
        remotePath: String,
        /** The user has answered the size question; open it. */
        force: Boolean = false,
        readOnly: Boolean = false,
        /** Set by "open as text anyway" on the not-text dialog. */
        asText: Boolean = false,
    ) {
        val session = sftpSession ?: return
        _editor.value = EditorState.Loading(remotePath)
        viewModelScope.launch(Dispatchers.IO) {
            val size = runCatching { session.sizeOf(remotePath) }.getOrNull()
            if (size != null && size > EDITOR_VIEW_MAX_BYTES) {
                _editor.value = EditorState.Unsupported(remotePath, EditorState.Reason.TOO_LARGE, size)
                return@launch
            }
            val bytes = try {
                session.readFile(remotePath, EDITOR_VIEW_MAX_BYTES)
            } catch (e: FileTooLargeException) {
                _editor.value = EditorState.Unsupported(remotePath, EditorState.Reason.TOO_LARGE, e.size)
                return@launch
            } catch (e: Exception) {
                editorFailed(remotePath, e)
                return@launch
            }
            hold(remotePath, bytes)
            decide(remotePath, bytes, force, readOnly, asText)
        }
    }

    /**
     * Answers the one question the file has already been read for, so nothing is transferred
     * twice: the dialogs that got here are choosing between editors, not asking for the file.
     */
    private fun decide(
        remotePath: String,
        bytes: ByteArray,
        force: Boolean,
        readOnly: Boolean,
        asText: Boolean,
    ) {
        val decoded = TextFile.decode(bytes, allowBinary = asText)
        if (decoded == null) {
            _editor.value = EditorState.Unsupported(remotePath, EditorState.Reason.BINARY, bytes.size.toLong())
            return
        }
        val size = bytes.size.toLong()
        _editor.value = when {
            readOnly || size <= EDITOR_WARN_BYTES ->
                EditorState.Ready(remotePath, decoded, readOnly = readOnly)
            // Past the editor's ceiling reading is all that is left, so the question loses its
            // "open anyway" and becomes an offer.
            size > EDITOR_MAX_BYTES -> EditorState.Confirm(remotePath, size, canEdit = false)
            force -> EditorState.Ready(remotePath, decoded)
            else -> EditorState.Confirm(remotePath, size, canEdit = true)
        }
    }

    /** Picks up a decision on the file already in hand — see [decide]. */
    fun continueEditor(readOnly: Boolean) {
        val path = _editor.value?.path ?: return
        val bytes = heldBytes(path) ?: run { openEditor(path, force = true, readOnly = readOnly); return }
        decide(path, bytes, force = true, readOnly = readOnly, asText = false)
    }

    /**
     * As [continueEditor], for "open as text anyway". Past the editor's ceiling it goes straight
     * to reading: the user has already answered one question about this file, and a second
     * dialog saying it is also too big would only be in the way — reading is the one thing left.
     */
    fun continueAsText() {
        val path = _editor.value?.path ?: return
        val bytes = heldBytes(path) ?: run { openEditor(path, force = true, asText = true); return }
        decide(path, bytes, force = true, readOnly = bytes.size > EDITOR_MAX_BYTES, asText = true)
    }

    /**
     * Reads the open file again as [charset] — the bytes are already here, so this only changes
     * how they are read. The caller has already dealt with any unsaved text; a charset that
     * cannot hold these bytes is not offered in the first place, so failing here is a bug.
     */
    fun setEditorCharset(charset: java.nio.charset.Charset) {
        val open = _editor.value as? EditorState.Ready ?: return
        val bytes = editorBytes ?: return
        val decoded = TextFile.decodeWith(bytes, charset, open.decoded.bom) ?: return
        _editor.value = open.copy(decoded = decoded, savedAt = 0L)
    }

    /**
     * Opens [remotePath] in the hex editor. It reaches as far as the read-only view does: the
     * rows are drawn by us, so nothing here costs what a text field costs.
     */
    fun openHex(remotePath: String) {
        heldBytes(remotePath)?.let {
            _editor.value = EditorState.Hex(remotePath, it.size)
            return
        }
        val session = sftpSession ?: return
        _editor.value = EditorState.Loading(remotePath)
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = try {
                session.readFile(remotePath, EDITOR_VIEW_MAX_BYTES)
            } catch (e: FileTooLargeException) {
                _editor.value = EditorState.Unsupported(remotePath, EditorState.Reason.TOO_LARGE, e.size)
                return@launch
            } catch (e: Exception) {
                editorFailed(remotePath, e)
                return@launch
            }
            hold(remotePath, bytes)
            _editor.value = EditorState.Hex(remotePath, bytes.size)
        }
    }

    /** The bytes the hex editor starts from; null once the editor is closed. */
    fun hexBytes(): ByteArray? = editorBytes

    /** Writes [bytes] back. The length never changes, so this replaces the file as it stands. */
    fun saveHex(bytes: ByteArray) {
        val open = _editor.value as? EditorState.Hex ?: return
        val session = sftpSession ?: return
        if (open.saving) return
        _editor.value = open.copy(saving = true, problem = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                session.writeFile(open.path, bytes)
            } catch (e: Exception) {
                if (sftpSession?.isConnected != true) {
                    editorFailed(open.path, e)
                } else {
                    _editor.value = open.copy(saving = false, problem = FileFailure.of(open.name, e))
                }
                return@launch
            }
            hold(open.path, bytes)
            _editor.value = open.copy(saving = false, savedAt = System.currentTimeMillis())
            refreshListing()
        }
    }

    /** The charsets the open file can be read as, for the picker. */
    fun editorCharsets(): List<java.nio.charset.Charset> =
        editorBytes?.let { TextFile.readableAs(it) } ?: emptyList()

    fun dismissEditorProblem() {
        _editor.update {
            when (it) {
                is EditorState.Ready -> it.copy(problem = null)
                is EditorState.Hex -> it.copy(problem = null)
                else -> it
            }
        }
    }

    /** Writes [text] back to the open file, keeping its encoding, line endings and mode. */
    fun saveEditor(text: String) {
        val open = _editor.value as? EditorState.Ready ?: return
        val session = sftpSession ?: return
        if (open.saving) return
        val bytes = TextFile.encode(text, open.decoded)
        if (bytes == null) {
            _editor.value = open.copy(problem = FileFailure(
                name    = "",
                message = str(R.string.editor_cannot_encode, open.decoded.charset.name()),
                detail  = "",
            ))
            return
        }
        _editor.value = open.copy(saving = true, problem = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                session.writeFile(open.path, bytes)
            } catch (e: Exception) {
                // A failed save must never cost the user their text, so the editor stays open
                // with the problem on top of it — unless the connection itself is gone.
                if (sftpSession?.isConnected != true) {
                    editorFailed(open.path, e)
                } else {
                    _editor.value = open.copy(saving = false, problem = FileFailure.of(open.name, e))
                }
                return@launch
            }
            // The text just written is the new baseline: the file on the server now matches it.
            hold(open.path, bytes)
            _editor.value = open.copy(
                decoded = open.decoded.copy(text = text),
                saving = false,
                savedAt = System.currentTimeMillis(),
            )
            refreshListing()
        }
    }

    /**
     * Reports a failed read or write. A dead connection goes through [report], which takes the
     * screen to the connection-lost error; anything else stays inside the editor, so the user
     * keeps the text they were working on and can try again.
     */
    private fun editorFailed(remotePath: String, e: Exception) {
        val failure = FileFailure.of(remotePath.substringAfterLast('/'), e)
        if (sftpSession?.isConnected != true) {
            _editor.value = null
            report(ErrorReport(str(R.string.editor_failed), listOf(failure)), refresh = false)
        } else {
            _editor.value = EditorState.Failed(remotePath, failure)
        }
    }

    fun closeEditor() {
        _editor.value = null
        editorBytes = null
        editorBytesPath = null
    }

    /** The last listing shown, to go back to when an operation fails without losing the connection. */
    @Volatile private var lastListing: State.Listing? = null

    init {
        viewModelScope.launch { _state.collect { if (it is State.Listing) lastListing = it } }
    }

    fun showReport(r: ErrorReport) { _report.value = r }

    /** Reopens the errors of a finished background transfer (a tap on its row). */
    fun showTransferReport(t: BackgroundTransfer) { _report.value = transferReport(t) }

    private fun transferReport(t: BackgroundTransfer) = ErrorReport(
        title = if (t.totalFiles == 1) str(R.string.error_download_failed)
                else str(R.string.sftp_report_downloaded_n_of_m, t.doneFiles, t.totalFiles),
        failures     = t.failures,
        notAttempted = t.notAttempted,
    )

    /**
     * Shows [r] — unless the connection is what failed. Then the listing can't come back, so the
     * screen goes to the connection-lost error carrying this report, which is the real cause:
     * refreshing first would only put its own "inputstream is closed" there instead.
     */
    private fun report(r: ErrorReport, refresh: Boolean) {
        if (sftpSession?.isConnected != true) {
            connectionLost(r.asText(getApplication()))
            return
        }
        // Never let a later failure (typically the refresh that follows) hide an earlier one:
        // the first report is the cause, anything after it is added below it.
        _report.update { shown -> shown?.let { it.copy(failures = it.failures + r.failures, notAttempted = it.notAttempted + r.notAttempted) } ?: r }
        if (refresh) refreshListing()
    }

    private fun connectionLost(detail: String?) {
        _state.value = State.Error(str(R.string.terminal_connection_lost), detail)
        sessionId?.let { id -> sessionManager.update(id) { s -> s.copy(status = SessionManager.Status.Error) } }
    }

    private fun str(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    /**
     * Whether dotfiles (names starting with ".") are shown. Persisted per-host on
     * [HostEntity.sftpShowHidden]; the toolbar toggle and the host editor both write that field, so
     * the choice is permanent for the host. Default is hidden. The screen filters on this flag, so
     * toggling is instant and never re-fetches the directory.
     */
    private val _showHidden = MutableStateFlow(false)
    val showHidden: StateFlow<Boolean> = _showHidden

    /**
     * App-wide preference: list folders before files. When off, entries are sorted
     * by name only. Applied at display time in the screen; never re-fetches.
     */
    val sortDirsFirst: StateFlow<Boolean> =
        sshBorgApp.appPreferences.sftpSortDirsFirst
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** Emitted when a file to be downloaded already exists in [downloadFolder]. */
    data class ConflictData(val entry: SftpEntry, val remotePath: String, val existingUri: Uri, val localDir: String)
    private val _conflictEvent = MutableSharedFlow<ConflictData>(extraBufferCapacity = 1)
    val conflictEvent: SharedFlow<ConflictData> = _conflictEvent

    private var sftpSession: SftpSession? = null
    private val pathStack = mutableListOf<String>()

    // Guards directory navigation. A single SFTP channel is not thread-safe, so two
    // overlapping listDir() calls corrupt the stream ("pipe closed"). Taps arriving
    // while a navigation is in flight are dropped, not queued — matching what the user
    // expects (a fast double-tap on a folder or on ".." should not walk several levels).
    private val navigating = java.util.concurrent.atomic.AtomicBoolean(false)

    private val hostKeyResult  = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    private val passwordResult = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** User's choice when a batch download finds existing files. */
    enum class BatchConflictDecision { OVERWRITE_ALL, SKIP_EXISTING, CANCEL }

    /** Emitted when a batch download finds that some files already exist locally. */
    data class BatchConflictData(val conflictCount: Int, val totalCount: Int)
    private val _batchConflictEvent = MutableSharedFlow<BatchConflictData>(extraBufferCapacity = 1)
    val batchConflictEvent: SharedFlow<BatchConflictData> = _batchConflictEvent
    private val batchConflictDecision = MutableSharedFlow<BatchConflictDecision>(extraBufferCapacity = 1)

    fun resolveBatchConflict(decision: BatchConflictDecision) { batchConflictDecision.tryEmit(decision) }

    /** Emitted when files about to be uploaded already exist in the remote folder. */
    sealed interface UploadConflict {
        data class Single(val name: String) : UploadConflict
        data class Batch(val conflictCount: Int, val totalCount: Int) : UploadConflict
    }
    enum class UploadDecision { OVERWRITE, KEEP_BOTH, SKIP_EXISTING, CANCEL }
    private val _uploadConflictEvent = MutableSharedFlow<UploadConflict>(extraBufferCapacity = 1)
    val uploadConflictEvent: SharedFlow<UploadConflict> = _uploadConflictEvent
    private val uploadDecision = MutableSharedFlow<UploadDecision>(extraBufferCapacity = 1)

    fun resolveUploadConflict(decision: UploadDecision) { uploadDecision.tryEmit(decision) }

    /** Job covering the Preparing phase of a batch download. */
    private var preparationJob: Job? = null
    /** ID of the transfer currently shown in the foreground (mirrored to [_state]). */
    private val _foregroundTransferId = MutableStateFlow<String?>(null)
    private var foregroundTransferId: String?
        get() = _foregroundTransferId.value
        set(value) { _foregroundTransferId.value = value }
    private var foregroundObserveJob: Job? = null

    // ── Session attach / connect ──────────────────────────────────────────────

    /**
     * Attaches to an existing SFTP session in [SessionManager].
     * If already connected, restores the last-known path.
     */
    fun attach(id: String) {
        sessionId = id
        seedShowHidden()
        val session = sessionManager.get(id) ?: run {
            _state.value = State.Error(getApplication<Application>().getString(R.string.error_session_not_found)); return
        }

        if (session.sftpSession != null) {
            sftpSession = session.sftpSession
            val path = session.sftpCurrentPath
            pathStack.clear()
            _state.value = State.Listing(path, emptyList())
            navigateTo(path)
        }
        // else: new session, connect() will be called next
    }

    /** Starts the SFTP connection for a newly created session. */
    /** Seeds [showHidden] from the current session's host row (called on attach). */
    private fun seedShowHidden() {
        val hostId = sessionId?.let { sessionManager.get(it)?.hostId } ?: return
        viewModelScope.launch { _showHidden.value = hostDao.getById(hostId)?.sftpShowHidden ?: false }
    }

    /** Flips dotfile visibility and persists it on the host, so the choice sticks for this host. */
    fun toggleHidden() {
        val hostId = sessionId?.let { sessionManager.get(it)?.hostId }
        val newValue = !_showHidden.value
        _showHidden.value = newValue
        if (hostId != null) viewModelScope.launch {
            hostDao.getById(hostId)?.let { hostDao.upsert(it.copy(sftpShowHidden = newValue)) }
        }
    }

    fun connect() {
        val id     = sessionId ?: return
        val hostId = sessionManager.get(id)?.hostId ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val host = hostDao.getById(hostId) ?: run {
                _state.value = State.Error(getApplication<Application>().getString(R.string.error_host_not_found)); return@launch
            }
            _showHidden.value = host.sftpShowHidden
            var auth = buildAuth(host) ?: return@launch
            var wrongPassword = false

            val jumpHostEntities = if (host.jumpMode == "host_list") {
                buildJumpHostEntities(host.jumpHostIdList)
            } else emptyList()

            while (true) {
                _state.value = State.Connecting

                val jumpHosts = if (host.jumpMode == "host_list") {
                    jumpHostEntities.mapNotNull { jumpHost ->
                        val jumpAuth = buildJumpAuth(jumpHost) ?: return@mapNotNull null
                        JumpHost(
                            host            = jumpHost.hostname,
                            port            = jumpHost.port,
                            username        = jumpHost.username,
                            knownHostsEntry = jumpHost.knownHostsEntry,
                            auth            = jumpAuth,
                            hostId          = jumpHost.id,
                        )
                    }
                } else {
                    parseJumpHosts(host.jumpHosts, host.jumpHostKeys)
                }

                val result = runCatching {
                    SshManager.openSftp(
                        SshConnectionParams(
                            hostname           = host.hostname,
                            port               = host.port,
                            username           = host.username,
                            auth               = auth,
                            agentForwarding    = host.agentForwarding,
                            knownHostsEntry    = host.knownHostsEntry,
                            jumpHosts          = jumpHosts,
                            portForwardings    = parsePortForwardings(host.portForwardings),
                            allowLegacyCiphers = host.allowLegacyCiphers,
                        )
                    ) { hostname, fingerprint, keyLine ->
                        runBlocking {
                            _state.value = State.HostKeyPrompt(hostname, fingerprint)
                            val accepted = hostKeyResult.first()
                            if (accepted) {
                                _state.value = State.Connecting
                                val current = hostDao.getById(hostId)
                                if (current != null) {
                                    if (hostname == host.hostname) {
                                        if (current.knownHostsEntry == null)
                                            hostDao.upsert(current.copy(knownHostsEntry = keyLine))
                                    } else if (host.jumpMode == "host_list") {
                                        val jumpEntity = jumpHostEntities.find { it.hostname == hostname }
                                        if (jumpEntity != null) {
                                            val jCurrent = hostDao.getById(jumpEntity.id)
                                            if (jCurrent != null && jCurrent.knownHostsEntry == null)
                                                hostDao.upsert(jCurrent.copy(knownHostsEntry = keyLine))
                                        }
                                    } else {
                                        val existing = current.jumpHostKeys
                                            ?.lines()?.filter { it.isNotBlank() } ?: emptyList()
                                        if (keyLine !in existing)
                                            hostDao.upsert(current.copy(jumpHostKeys = (existing + keyLine).joinToString("\n")))
                                    }
                                }
                            }
                            accepted
                        }
                    }
                }

                val session = result.getOrNull()
                if (session != null) {
                    sftpSession = session
                    sessionManager.update(id) { it.copy(sftpSession = session, status = SessionManager.Status.Connected) }
                    val saved = hostDao.getById(hostId) ?: host
                    if (saved.knownHostsEntry == null)
                        hostDao.upsert(saved.copy(knownHostsEntry = session.hostKeyLine))
                    if (session.newJumpHostKeyLines.isNotEmpty()) {
                        val current = hostDao.getById(hostId) ?: saved
                        val existing = current.jumpHostKeys?.lines()?.filter { it.isNotBlank() } ?: emptyList()
                        val toAdd = session.newJumpHostKeyLines.filter { it !in existing }
                        if (toAdd.isNotEmpty())
                            hostDao.upsert(current.copy(jumpHostKeys = (existing + toAdd).joinToString("\n")))
                    }
                    for ((jumpHostId, keyLine) in session.newJumpHostKeyUpdates) {
                        val jCurrent = hostDao.getById(jumpHostId) ?: continue
                        if (jCurrent.knownHostsEntry == null)
                            hostDao.upsert(jCurrent.copy(knownHostsEntry = keyLine))
                    }
                    val startPaths = when (host.sftpStartMode) {
                        "last", "fixed" -> listOfNotNull(
                            host.sftpStartDir?.takeIf { it.isNotBlank() },
                            session.homePath,
                            "/",
                        )
                        else -> listOf(session.homePath, "/")
                    }
                    for (path in startPaths) {
                        val entries = runCatching { sftpSession!!.listDir(path) }.getOrNull() ?: continue
                        pathStack.add(path)
                        sessionManager.update(id) { it.copy(sftpCurrentPath = path) }
                        _state.value = State.Listing(path, entries)
                        return@launch
                    }
                    _state.value = State.Error(getApplication<Application>().getString(R.string.error_cannot_list_directory))
                    sessionManager.update(id) { it.copy(status = SessionManager.Status.Error) }
                    return@launch
                }

                val err = result.exceptionOrNull()
                if (isAuthFailure(err) && auth !is SshAuth.PublicKey) {
                    _state.value = State.PasswordPrompt(host.hostname, wrongPassword = true)
                    val pwd = passwordResult.first()
                    if (pwd.isEmpty()) {
                        _state.value = State.Disconnected
                        sessionManager.update(id) { it.copy(status = SessionManager.Status.Error) }
                        return@launch
                    }
                    auth = SshAuth.Password(pwd)
                } else {
                    _state.value = State.Error(
                        err?.message ?: getApplication<Application>().getString(R.string.error_connection_failed),
                        err?.stackTraceToString(),
                    )
                    sessionManager.update(id) { it.copy(status = SessionManager.Status.Error) }
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

    // ── Navigation ────────────────────────────────────────────────────────────

    fun navigateTo(path: String) {
        // Drop the tap if a navigation is already running (see [navigating]).
        if (!navigating.compareAndSet(false, true)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                runCatching {
                    val entries = sftpSession!!.listDir(path)
                    pathStack.add(path)
                    sessionManager.update(sessionId ?: return@launch) { it.copy(sftpCurrentPath = path) }
                    _state.value = State.Listing(path, entries)
                }.onFailure {
                    report(ErrorReport(str(R.string.error_cannot_list_directory), listOf(FileFailure.of(path, it))), refresh = false)
                }
            } finally {
                navigating.set(false)
            }
        }
    }

    fun navigateUp(): Boolean {
        // Busy: swallow the request (return "handled") so a hardware-back during a
        // navigation neither pops the stack nor falls through to disconnect().
        if (navigating.get()) return true
        val current = (state.value as? State.Listing)?.path ?: return false
        if (current == "/" || current.isEmpty()) return false
        val parent = current.substringBeforeLast("/").ifEmpty { "/" }
        if (pathStack.isNotEmpty()) pathStack.removeAt(pathStack.lastIndex)
        navigateTo(parent)
        return true
    }

    // ── Single-file download (shows conflict dialog on collision) ─────────────

    fun downloadFile(entry: SftpEntry, currentPath: String) {
        val session = sftpSession ?: return
        val id = sessionId ?: return
        val remotePath = "${currentPath.trimEnd('/')}/${entry.name}"
        viewModelScope.launch(Dispatchers.IO) {
            val existing = findExistingDownload(entry.name, downloadFolder)
            if (existing != null) {
                _conflictEvent.tryEmit(ConflictData(entry, remotePath, existing, downloadFolder))
                return@launch
            }
            startForegroundObservation(transferManager.enqueue(id, session, remotePath, entry.name))
        }
    }

    fun downloadOverwrite(conflict: ConflictData) {
        val session = sftpSession ?: return
        val id = sessionId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            getApplication<Application>().contentResolver.delete(conflict.existingUri, null, null)
            startForegroundObservation(transferManager.enqueue(id, session, conflict.remotePath, conflict.entry.name, conflict.localDir))
        }
    }

    fun downloadKeepBoth(conflict: ConflictData) {
        val session = sftpSession ?: return
        val id = sessionId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val unique = uniqueFilename(conflict.entry.name, conflict.localDir)
            startForegroundObservation(transferManager.enqueue(id, session, conflict.remotePath, unique, conflict.localDir))
        }
    }

    // ── Batch download (files + folders; skips existing files silently) ───────

    /**
     * Downloads [entries] (files and/or folders) from [currentPath].
     * Folders are expanded recursively. Files that already exist locally are skipped.
     * Cancellable via [cancelDownload].
     */
    fun downloadEntries(entries: List<SftpEntry>, currentPath: String) {
        val session = sftpSession ?: return
        val id = sessionId ?: return
        val context = getApplication<Application>()
        preparationJob = viewModelScope.launch(Dispatchers.IO) {
            _state.value = State.Preparing

            // Phase 1: collect all file tasks (expand folders recursively)
            val allTasks = mutableListOf<TransferTask>()
            val unlisted = mutableListOf<FileFailure>()
            for (entry in entries) {
                ensureActive()
                val entryPath = "${currentPath.trimEnd('/')}/${entry.name}"
                if (entry.isDir && !entry.isLink) {
                    collectDirTasks(entryPath, "$downloadFolder${entry.name}/", allTasks, unlisted)
                } else if (!entry.isDir) {
                    allTasks.add(TransferTask(entryPath, entry.name, downloadFolder))
                }
            }

            if (allTasks.isEmpty()) {
                if (unlisted.isEmpty()) {
                    _opError.tryEmit(context.getString(R.string.sftp_nothing_to_download))
                    refreshListing()
                } else {
                    report(ErrorReport(str(R.string.error_download_failed), unlisted), refresh = true)
                }
                return@launch
            }

            // Phase 2: detect conflicts and ask the user how to proceed
            val conflicting = allTasks.filter { findExistingDownload(it.filename, it.localDir) != null }
            val tasksToDownload: List<TransferTask>
            if (conflicting.isEmpty()) {
                tasksToDownload = allTasks
            } else {
                _batchConflictEvent.tryEmit(BatchConflictData(conflicting.size, allTasks.size))
                when (batchConflictDecision.first()) {
                    BatchConflictDecision.CANCEL -> { refreshListing(); return@launch }
                    BatchConflictDecision.SKIP_EXISTING -> {
                        tasksToDownload = allTasks.filter { it !in conflicting }
                    }
                    BatchConflictDecision.OVERWRITE_ALL -> {
                        conflicting.forEach { task ->
                            findExistingDownload(task.filename, task.localDir)
                                ?.let { context.contentResolver.delete(it, null, null) }
                        }
                        tasksToDownload = allTasks
                    }
                }
            }

            if (tasksToDownload.isEmpty()) { refreshListing(); return@launch }

            preparationJob = null
            startForegroundObservation(transferManager.enqueue(id, session, tasksToDownload, unlisted))
        }
    }

    fun cancelDownload() {
        preparationJob?.cancel()
        preparationJob = null
        val tid = foregroundTransferId
        foregroundObserveJob?.cancel()
        foregroundObserveJob = null
        foregroundTransferId = null
        // Dismiss too: the observer that normally dismisses terminal transfers was just
        // cancelled, so without this the entry would linger in the background panel.
        tid?.let { transferManager.cancel(it); transferManager.dismiss(it) }
        refreshListing()
    }

    fun sendToBackground() {
        foregroundObserveJob?.cancel()
        foregroundObserveJob = null
        foregroundTransferId = null
        refreshListing()
    }

    private fun startForegroundObservation(transferId: String) {
        foregroundObserveJob?.cancel()
        foregroundTransferId = transferId
        foregroundObserveJob = viewModelScope.launch {
            val thisJob = coroutineContext[Job]!!
            transferManager.transfers
                .mapNotNull { list -> list.find { it.id == transferId } }
                .collect { t ->
                    when (t.status) {
                        BackgroundTransfer.Status.Running ->
                            _state.value = State.Downloading(t.filename, t.localDir, t.bytesReceived, t.fileIndex, t.totalFiles, t.startedAt)
                        BackgroundTransfer.Status.Done -> {
                            transferManager.dismiss(transferId)
                            val failed = t.failures.takeIf { it.isNotEmpty() }?.let { transferReport(t) }
                            _state.value = State.Downloaded(
                                t.filename, t.localDir, t.totalFiles, t.skippedFiles,
                                t.startedAt, t.completedAt ?: System.currentTimeMillis(),
                                failed,
                            )
                            // The user watched this one: show what was skipped right away.
                            failed?.let { _report.value = it }
                            foregroundTransferId = null
                            foregroundObserveJob = null
                            thisJob.cancel()
                        }
                        BackgroundTransfer.Status.Error -> {
                            transferManager.dismiss(transferId)
                            report(transferReport(t), refresh = true)
                            foregroundTransferId = null
                            foregroundObserveJob = null
                            thisJob.cancel()
                        }
                        BackgroundTransfer.Status.Cancelled -> {
                            transferManager.dismiss(transferId)
                            refreshListing()
                            foregroundTransferId = null
                            foregroundObserveJob = null
                            thisJob.cancel()
                        }
                    }
                }
        }
    }

    // ── Background downloads (from context menu — never blocks foreground) ──────

    /** Transfers for this session visible in the background panel (excludes the foreground transfer). */
    val backgroundTransfers: StateFlow<List<BackgroundTransfer>> =
        combine(transferManager.transfers, _foregroundTransferId) { transfers, fgId ->
            transfers.filter { it.sessionId == sessionId && it.id != fgId }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Starts a background download without blocking the UI. Skips if the file already exists. */
    fun downloadFileInBackground(entry: SftpEntry, currentPath: String) {
        val session = sftpSession ?: return
        val id = sessionId ?: return
        val remotePath = "${currentPath.trimEnd('/')}/${entry.name}"
        if (findExistingDownload(entry.name, downloadFolder) != null) {
            _opError.tryEmit(getApplication<Application>().getString(R.string.sftp_background_skipped_exists, entry.name))
            return
        }
        transferManager.enqueue(id, session, remotePath, entry.name)
    }

    fun cancelBackgroundTransfer(transferId: String) = transferManager.cancel(transferId)
    fun dismissBackgroundTransfer(transferId: String) = transferManager.dismiss(transferId)

    // ── Download helpers ──────────────────────────────────────────────────────

    fun dismissDownloaded() = refreshListing()

    private suspend fun collectDirTasks(
        remotePath: String,
        localDir: String,
        tasks: MutableList<TransferTask>,
        unlisted: MutableList<FileFailure>,
    ) {
        runCatching {
            val entries = sftpSession!!.listDir(remotePath)
            for (entry in entries) {
                val entryPath = "$remotePath/${entry.name}"
                if (entry.isDir && !entry.isLink) {
                    collectDirTasks(entryPath, "$localDir${entry.name}/", tasks, unlisted)
                } else if (!entry.isDir) {
                    tasks.add(TransferTask(entryPath, entry.name, localDir))
                }
            }
        }.onFailure { unlisted += FileFailure.of(remotePath, it) }   // the rest of the batch continues
    }

    /**
     * Opens a completed download: resolves its MediaStore uri from name + folder and fires the
     * shared [DownloadIntents] intent (view the file, or the Downloads screen for an APK). Fails
     * silently if the file is gone or nothing can open it.
     */
    fun openDownloadedFile(filename: String, localDir: String) {
        val uri = findExistingDownload(filename, localDir) ?: return
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(filename.substringAfterLast('.', "").lowercase())
        runCatching {
            getApplication<Application>().startActivity(DownloadIntents.open(uri, mime, filename))
        }
    }

    private fun findExistingDownload(filename: String, localDir: String): Uri? {
        val context = getApplication<Application>()
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND " +
                        "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(filename, "%${localDir.trimEnd('/')}%")
        return context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection, selection, selectionArgs, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            } else null
        }
    }

    /** Returns a name like "file(1).txt" that is not in [taken] (the remote folder's names). */
    private fun uniqueRemoteName(original: String, taken: Set<String>): String {
        val dot = original.lastIndexOf('.')
        val base = if (dot > 0) original.substring(0, dot) else original
        val ext  = if (dot > 0) original.substring(dot) else ""
        return generateSequence(1) { it + 1 }.map { "$base($it)$ext" }.first { it !in taken }
    }

    /** Returns a filename like "file(1).txt" that does not yet exist in [localDir]. */
    private fun uniqueFilename(original: String, localDir: String): String {
        val dot = original.lastIndexOf('.')
        val base = if (dot > 0) original.substring(0, dot) else original
        val ext  = if (dot > 0) original.substring(dot) else ""
        var counter = 1
        while (true) {
            val candidate = "$base($counter)$ext"
            if (findExistingDownload(candidate, localDir) == null) return candidate
            counter++
        }
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    fun uploadFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val context = getApplication<Application>()
        val currentPath = (state.value as? State.Listing)?.path ?: return
        val total = uris.size

        viewModelScope.launch(Dispatchers.IO) {
            // Names first, so files a lost connection never reaches can still be listed.
            val names = uris.map { uri ->
                context.contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    ?: uri.lastPathSegment ?: "file"
            }

            // Ask before replacing anything already on the server. The names come from a fresh
            // listing, not the one on screen, which may be stale.
            val existing = runCatching { sftpSession!!.listDir(currentPath).map { it.name }.toSet() }
                .getOrElse {
                    report(ErrorReport(str(R.string.error_upload_failed), listOf(FileFailure.of(currentPath, it))), refresh = true)
                    return@launch
                }
            val clashing = names.filter { it in existing }
            var targets = names   // remote name for each uri; null = skipped
                .map<String, String?> { it }
            if (clashing.isNotEmpty()) {
                _uploadConflictEvent.tryEmit(
                    if (total == 1) UploadConflict.Single(names.first())
                    else UploadConflict.Batch(clashing.size, total)
                )
                when (uploadDecision.first()) {
                    UploadDecision.CANCEL -> return@launch
                    UploadDecision.OVERWRITE -> Unit
                    UploadDecision.KEEP_BOTH -> {
                        val taken = existing.toMutableSet()
                        targets = names.map { n -> if (n in existing) uniqueRemoteName(n, taken).also { taken += it } else n }
                    }
                    UploadDecision.SKIP_EXISTING -> targets = names.map { n -> n.takeIf { it !in existing } }
                }
            }
            val plan = uris.indices.mapNotNull { i -> targets[i]?.let { uris[i] to it } }
            if (plan.isEmpty()) { refreshListing(); return@launch }
            val planTotal = plan.size

            val failures = mutableListOf<FileFailure>()
            var notAttempted = emptyList<String>()
            var uploaded = 0
            for ((index, item) in plan.withIndex()) {
                val (uri, filename) = item
                // One failed file doesn't stop the batch, but a dead connection does: every
                // remaining file would fail the same way.
                if (sftpSession?.isConnected != true) {
                    notAttempted = plan.drop(index).map { it.second }
                    break
                }
                val remotePath = "${currentPath.trimEnd('/')}/$filename"
                _state.value = State.Uploading(filename, 0L, index + 1, planTotal)
                runCatching {
                    context.contentResolver.openInputStream(uri)!!.use { stream ->
                        sftpSession!!.uploadFile(stream, remotePath) { bytes ->
                            _state.value = State.Uploading(filename, bytes, index + 1, planTotal)
                        }
                    }
                }.onSuccess { uploaded++ }
                 .onFailure { failures += FileFailure.of(filename, it) }
            }
            if (failures.isEmpty() && notAttempted.isEmpty()) {
                // The view model refreshes by itself; the screen only shows the notice. (It used
                // to be the screen, from an effect on an "Uploaded" state — an effect that can
                // run twice, and did: two listings at once on the one channel.)
                val msg = if (planTotal == 1) str(R.string.sftp_uploaded, plan.last().second)
                          else str(R.string.sftp_uploaded_n_files, planTotal)
                _opError.tryEmit(msg)
                SshForegroundService.notifyUploadComplete(getApplication(), msg)
                refreshListing()
                return@launch
            }
            val title = if (planTotal == 1) str(R.string.error_upload_failed)
                        else str(R.string.sftp_report_uploaded_n_of_m, uploaded, planTotal)
            report(ErrorReport(title, failures, notAttempted), refresh = true)
        }
    }

    // ── File operations ───────────────────────────────────────────────────────

    private var deleteJob: Job? = null

    fun deleteEntry(entry: SftpEntry, currentPath: String) {
        val path = "${currentPath.trimEnd('/')}/${entry.name}"
        deleteJob = viewModelScope.launch(Dispatchers.IO) {
            coroutineContext[Job]!!.invokeOnCompletion { cause ->
                if (cause is CancellationException) refreshListing()
            }
            _state.value = State.Deleting(entry.name)
            runCatching {
                if (entry.isDir && !entry.isLink) deleteRecursive(path) else sftpSession!!.deleteFile(path)
            }.onFailure {
                if (it is CancellationException) throw it
                report(ErrorReport(str(R.string.error_delete_failed), listOf(FileFailure.of(entry.name, it))), refresh = true)
                return@launch
            }
            refreshListing()
        }
    }

    fun deleteEntries(entries: List<SftpEntry>, currentPath: String) {
        val total = entries.size
        deleteJob = viewModelScope.launch(Dispatchers.IO) {
            coroutineContext[Job]!!.invokeOnCompletion { cause ->
                if (cause is CancellationException) refreshListing()
            }
            val failures = mutableListOf<FileFailure>()
            var notAttempted = emptyList<String>()
            for ((index, entry) in entries.withIndex()) {
                ensureActive()
                if (sftpSession?.isConnected != true) {
                    notAttempted = entries.drop(index).map { it.name }
                    break
                }
                _state.value = State.Deleting(entry.name, index + 1, total)
                val path = "${currentPath.trimEnd('/')}/${entry.name}"
                runCatching {
                    if (entry.isDir && !entry.isLink) deleteRecursive(path, index + 1, total)
                    else sftpSession!!.deleteFile(path)
                }.onFailure {
                    if (it is CancellationException) throw it
                    failures += FileFailure.of(entry.name, it)
                }
            }
            if (failures.isEmpty() && notAttempted.isEmpty()) refreshListing()
            else report(ErrorReport(str(R.string.error_delete_failed), failures, notAttempted), refresh = true)
        }
    }

    fun cancelDelete() {
        deleteJob?.cancel()
        deleteJob = null
    }

    private suspend fun deleteRecursive(path: String, index: Int = 1, total: Int = 1) {
        for (entry in sftpSession!!.listDir(path)) {
            currentCoroutineContext().ensureActive()
            val childPath = "$path/${entry.name}"
            _state.value = State.Deleting(entry.name, index, total)
            // Never recurse into symlinks even if they report isDir=true
            if (entry.isDir && !entry.isLink) deleteRecursive(childPath, index, total)
            else sftpSession!!.deleteFile(childPath)
        }
        sftpSession!!.deleteDir(path)
    }

    fun renameEntry(entry: SftpEntry, currentPath: String, newName: String) {
        val oldPath = "${currentPath.trimEnd('/')}/${entry.name}"
        val newPath = "${currentPath.trimEnd('/')}/$newName"
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { sftpSession!!.rename(oldPath, newPath) }
                .onSuccess { refreshListing() }
                .onFailure { report(ErrorReport(str(R.string.error_rename_failed), listOf(FileFailure.of(entry.name, it))), refresh = false) }
        }
    }

    fun createDirectory(currentPath: String, name: String) {
        val path = "${currentPath.trimEnd('/')}/$name"
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { sftpSession!!.mkdir(path) }
                .onSuccess { refreshListing() }
                .onFailure { report(ErrorReport(str(R.string.error_create_directory_failed), listOf(FileFailure.of(name, it))), refresh = false) }
        }
    }

    fun refreshListing() {
        val current = pathStack.lastOrNull() ?: return
        // One refresh at a time: a request arriving while one runs is folded into a single
        // rerun when it ends, so bursts of requests never become parallel listings.
        if (!refreshing.compareAndSet(false, true)) { refreshAgain = true; return }
        viewModelScope.launch(Dispatchers.IO) {
            try { doRefresh(current) } finally {
                refreshing.set(false)
                if (refreshAgain) { refreshAgain = false; refreshListing() }
            }
        }
    }

    private val refreshing = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var refreshAgain = false

    private fun doRefresh(current: String) {
        runCatching {
            _state.value = State.Listing(current, sftpSession!!.listDir(current), System.currentTimeMillis())
        }.onFailure {
            // If the session is gone, report() goes to the connection-lost screen: a dialog
            // alone would leave the current state (e.g. the Downloading overlay) with no way out.
            report(ErrorReport(str(R.string.error_refresh_failed), listOf(FileFailure.of(current, it))), refresh = false)
            // Still connected: get off the progress screen that launched this refresh, back
            // to the listing we had, so the screen doesn't hang on a spinner.
            if (sftpSession?.isConnected == true && _state.value !is State.Listing) {
                lastListing?.let { l -> _state.value = l }
            }
        }
    }

    // ── Auth callbacks ────────────────────────────────────────────────────────

    fun acceptHostKey() { hostKeyResult.tryEmit(true) }
    fun rejectHostKey() { hostKeyResult.tryEmit(false) }
    fun submitPassword(pwd: String) { passwordResult.tryEmit(pwd) }

    // ── Session lifecycle ─────────────────────────────────────────────────────

    /** Disconnects and removes the session from [SessionManager]. */
    fun disconnect() {
        preparationJob?.cancel()
        preparationJob = null
        foregroundObserveJob?.cancel()
        foregroundObserveJob = null
        foregroundTransferId = null
        deleteJob?.cancel()
        deleteJob = null
        sessionId?.let { transferManager.cancelBySession(it) }
        val id = sessionId ?: return
        val hostId   = sessionManager.get(id)?.hostId
        val lastPath = pathStack.lastOrNull()
        sftpSession = null
        pathStack.clear()
        sessionManager.remove(id)
        _state.value = State.Disconnected
        if (sessionManager.sessions.value.isEmpty()) {
            SshForegroundService.stop(getApplication())
        }
        if (hostId != null && !lastPath.isNullOrBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                val host = hostDao.getById(hostId) ?: return@launch
                if (host.sftpStartMode == "last") {
                    hostDao.upsert(host.copy(sftpStartDir = lastPath))
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up sessions that never fully connected (connecting or failed)
        val id = sessionId ?: return
        val status = sessionManager.get(id)?.status
        if (status == SessionManager.Status.Connecting || status == SessionManager.Status.Error) {
            sessionManager.remove(id)
            if (sessionManager.sessions.value.isEmpty()) {
                SshForegroundService.stop(getApplication())
            }
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private suspend fun buildJumpHostEntities(idList: String?): List<HostEntity> {
        if (idList.isNullOrBlank()) return emptyList()
        return idList.split(",").mapNotNull { it.trim().toLongOrNull() }
            .mapNotNull { hostDao.getById(it) }
    }

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
            _state.value = State.PasswordPrompt(host.hostname)
            val pwd = passwordResult.first()
            if (pwd.isEmpty()) { _state.value = State.Disconnected; null }
            else SshAuth.Password(pwd)
        }
    }
}
