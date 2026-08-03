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
import com.sshborg.service.BackgroundTransfer
import com.sshborg.service.SessionManager
import com.sshborg.service.SshForegroundService
import com.sshborg.service.TransferTask
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class SftpViewModel(app: Application) : AndroidViewModel(app) {

    sealed interface State {
        object Connecting : State
        object Preparing : State
        data class HostKeyPrompt(val hostname: String, val fingerprint: String) : State
        data class PasswordPrompt(val hostname: String, val wrongPassword: Boolean = false) : State
        data class Listing(val path: String, val entries: List<SftpEntry>, val nonce: Long = 0L) : State
        data class Downloading(val filename: String, val bytesReceived: Long, val fileIndex: Int = 1, val totalFiles: Int = 1, val startedAt: Long = 0L) : State
        data class Downloaded(
            val filename: String,
            val totalFiles: Int = 1,
            val skippedFiles: Int = 0,
            val startedAt: Long = 0L,
            val completedAt: Long = 0L,
        ) : State
        data class Deleting(val name: String, val index: Int = 1, val total: Int = 1) : State
        data class Uploading(val filename: String, val bytesSent: Long, val fileIndex: Int = 1, val totalFiles: Int = 1) : State
        data class Uploaded(val filename: String, val totalFiles: Int = 1) : State
        data class Error(val message: String) : State
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

    private val _opError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val opError: SharedFlow<String> = _opError

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
    fun connect() {
        val id     = sessionId ?: return
        val hostId = sessionManager.get(id)?.hostId ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val host = hostDao.getById(hostId) ?: run {
                _state.value = State.Error(getApplication<Application>().getString(R.string.error_host_not_found)); return@launch
            }
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
                    _state.value = State.Error(err?.message ?: getApplication<Application>().getString(R.string.error_connection_failed))
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
                    _opError.tryEmit(it.message ?: getApplication<Application>().getString(R.string.error_cannot_list_directory))
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
            for (entry in entries) {
                ensureActive()
                val entryPath = "${currentPath.trimEnd('/')}/${entry.name}"
                if (entry.isDir && !entry.isLink) {
                    collectDirTasks(entryPath, "$downloadFolder${entry.name}/", allTasks)
                } else if (!entry.isDir) {
                    allTasks.add(TransferTask(entryPath, entry.name, downloadFolder))
                }
            }

            if (allTasks.isEmpty()) {
                _opError.tryEmit(context.getString(R.string.sftp_nothing_to_download))
                refreshListing()
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
            startForegroundObservation(transferManager.enqueue(id, session, tasksToDownload))
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
                            _state.value = State.Downloading(t.filename, t.bytesReceived, t.fileIndex, t.totalFiles, t.startedAt)
                        BackgroundTransfer.Status.Done -> {
                            transferManager.dismiss(transferId)
                            _state.value = State.Downloaded(
                                t.filename, t.totalFiles, t.skippedFiles,
                                t.startedAt, t.completedAt ?: System.currentTimeMillis(),
                            )
                            foregroundTransferId = null
                            foregroundObserveJob = null
                            thisJob.cancel()
                        }
                        BackgroundTransfer.Status.Error -> {
                            transferManager.dismiss(transferId)
                            refreshListing()
                            _opError.tryEmit(getApplication<Application>().getString(R.string.error_download_failed))
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
    ) {
        runCatching {
            val entries = sftpSession!!.listDir(remotePath)
            for (entry in entries) {
                val entryPath = "$remotePath/${entry.name}"
                if (entry.isDir && !entry.isLink) {
                    collectDirTasks(entryPath, "$localDir${entry.name}/", tasks)
                } else if (!entry.isDir) {
                    tasks.add(TransferTask(entryPath, entry.name, localDir))
                }
            }
        } // swallow per-subtree errors — remaining tasks continue
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
            var lastFilename = ""
            for ((index, uri) in uris.withIndex()) {
                val filename = context.contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    ?: uri.lastPathSegment ?: "file"
                lastFilename = filename
                val remotePath = "${currentPath.trimEnd('/')}/$filename"
                _state.value = State.Uploading(filename, 0L, index + 1, total)
                val success = runCatching {
                    context.contentResolver.openInputStream(uri)!!.use { stream ->
                        sftpSession!!.uploadFile(stream, remotePath) { bytes ->
                            _state.value = State.Uploading(filename, bytes, index + 1, total)
                        }
                    }
                }.onFailure {
                    refreshListing()
                    _opError.tryEmit(it.message ?: context.getString(R.string.error_upload_failed))
                }.isSuccess
                if (!success) return@launch
            }
            val s = State.Uploaded(lastFilename, total)
            _state.value = s; postUploadNotification(s)
        }
    }

    fun dismissUploaded() = refreshListing()

    private fun postUploadNotification(s: State.Uploaded) {
        val ctx = getApplication<Application>()
        val msg = if (s.totalFiles == 1)
            ctx.getString(R.string.sftp_uploaded, s.filename)
        else
            ctx.getString(R.string.sftp_uploaded_n_files, s.totalFiles)
        SshForegroundService.notifyUploadComplete(ctx, msg)
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
            }.onFailure { _opError.tryEmit(it.message ?: getApplication<Application>().getString(R.string.error_delete_failed)) }
            refreshListing()
        }
    }

    fun deleteEntries(entries: List<SftpEntry>, currentPath: String) {
        val total = entries.size
        deleteJob = viewModelScope.launch(Dispatchers.IO) {
            coroutineContext[Job]!!.invokeOnCompletion { cause ->
                if (cause is CancellationException) refreshListing()
            }
            for ((index, entry) in entries.withIndex()) {
                ensureActive()
                _state.value = State.Deleting(entry.name, index + 1, total)
                val path = "${currentPath.trimEnd('/')}/${entry.name}"
                runCatching {
                    if (entry.isDir && !entry.isLink) deleteRecursive(path, index + 1, total)
                    else sftpSession!!.deleteFile(path)
                }.onFailure {
                    _opError.tryEmit(it.message ?: getApplication<Application>().getString(R.string.error_delete_failed))
                }
            }
            refreshListing()
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
            runCatching { sftpSession!!.rename(oldPath, newPath); refreshListing() }
                .onFailure { _opError.tryEmit(it.message ?: getApplication<Application>().getString(R.string.error_rename_failed)) }
        }
    }

    fun createDirectory(currentPath: String, name: String) {
        val path = "${currentPath.trimEnd('/')}/$name"
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { sftpSession!!.mkdir(path); refreshListing() }
                .onFailure { _opError.tryEmit(it.message ?: getApplication<Application>().getString(R.string.error_create_directory_failed)) }
        }
    }

    fun refreshListing() {
        val current = pathStack.lastOrNull() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                _state.value = State.Listing(current, sftpSession!!.listDir(current), System.currentTimeMillis())
            }.onFailure {
                // If the session is gone, a snackbar alone would leave the current state
                // (e.g. the Downloading overlay) on screen with no way out.
                if (sftpSession?.isConnected != true) {
                    _state.value = State.Error(getApplication<Application>().getString(R.string.terminal_connection_lost))
                    sessionId?.let { id -> sessionManager.update(id) { s -> s.copy(status = SessionManager.Status.Error) } }
                } else {
                    _opError.tryEmit(it.message ?: getApplication<Application>().getString(R.string.error_refresh_failed))
                }
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
