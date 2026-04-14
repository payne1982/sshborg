package com.sshborg.ui.sftp

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import com.sshborg.BuildConfig
import com.sshborg.R
import androidx.lifecycle.viewModelScope
import com.sshborg.SshBorgApp
import com.sshborg.data.db.HostEntity
import com.sshborg.data.KeystoreManager
import com.sshborg.data.ssh.*
import com.sshborg.service.SessionManager
import com.sshborg.service.SshForegroundService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class SftpViewModel(app: Application) : AndroidViewModel(app) {

    sealed interface State {
        object Connecting : State
        object Preparing : State
        data class HostKeyPrompt(val hostname: String, val fingerprint: String) : State
        data class PasswordPrompt(val hostname: String, val wrongPassword: Boolean = false) : State
        data class Listing(val path: String, val entries: List<SftpEntry>, val nonce: Long = 0L) : State
        data class Downloading(val filename: String, val bytesReceived: Long, val fileIndex: Int = 1, val totalFiles: Int = 1) : State
        data class Downloaded(val filename: String, val totalFiles: Int = 1, val skippedFiles: Int = 0) : State
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

    private val sshBorgApp     = app as SshBorgApp
    private val sessionManager = sshBorgApp.sessionManager
    private val hostDao        = sshBorgApp.db.hostDao()
    private val keyDao         = sshBorgApp.db.sshKeyDao()

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

    /** Job for batch downloads (downloadEntries); cancellable via [cancelDownload]. */
    private var batchDownloadJob: Job? = null

    private data class DownloadTask(
        val remotePath: String,
        val filename: String,
        val localDir: String,
    )

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
                            hostname        = host.hostname,
                            port            = host.port,
                            username        = host.username,
                            auth            = auth,
                            agentForwarding = host.agentForwarding,
                            knownHostsEntry = host.knownHostsEntry,
                            jumpHosts       = jumpHosts,
                            portForwardings = parsePortForwardings(host.portForwardings),
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
                    navigateTo(session.homePath)
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
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val entries = sftpSession!!.listDir(path)
                pathStack.add(path)
                sessionManager.update(sessionId ?: return@launch) { it.copy(sftpCurrentPath = path) }
                _state.value = State.Listing(path, entries)
            }.onFailure {
                _opError.tryEmit(it.message ?: getApplication<Application>().getString(R.string.error_cannot_list_directory))
            }
        }
    }

    fun navigateUp(): Boolean {
        val current = (state.value as? State.Listing)?.path ?: return false
        if (current == "/" || current.isEmpty()) return false
        val parent = current.substringBeforeLast("/").ifEmpty { "/" }
        if (pathStack.isNotEmpty()) pathStack.removeAt(pathStack.lastIndex)
        navigateTo(parent)
        return true
    }

    // ── Single-file download (shows conflict dialog on collision) ─────────────

    fun downloadFile(entry: SftpEntry, currentPath: String) {
        val remotePath = "${currentPath.trimEnd('/')}/${entry.name}"
        viewModelScope.launch(Dispatchers.IO) {
            val existing = findExistingDownload(entry.name, downloadFolder)
            if (existing != null) {
                _conflictEvent.tryEmit(ConflictData(entry, remotePath, existing, downloadFolder))
                return@launch
            }
            performDownload(entry, entry.name, remotePath, downloadFolder)
        }
    }

    fun downloadOverwrite(conflict: ConflictData) {
        viewModelScope.launch(Dispatchers.IO) {
            getApplication<Application>().contentResolver.delete(conflict.existingUri, null, null)
            performDownload(conflict.entry, conflict.entry.name, conflict.remotePath, conflict.localDir)
        }
    }

    fun downloadKeepBoth(conflict: ConflictData) {
        viewModelScope.launch(Dispatchers.IO) {
            val unique = uniqueFilename(conflict.entry.name, conflict.localDir)
            performDownload(null, unique, conflict.remotePath, conflict.localDir)
        }
    }

    // ── Batch download (files + folders; skips existing files silently) ───────

    /**
     * Downloads [entries] (files and/or folders) from [currentPath].
     * Folders are expanded recursively. Files that already exist locally are skipped.
     * Cancellable via [cancelDownload].
     */
    fun downloadEntries(entries: List<SftpEntry>, currentPath: String) {
        val context = getApplication<Application>()
        batchDownloadJob = viewModelScope.launch(Dispatchers.IO) {
            // On cancellation (user pressed cancel), restore the listing
            coroutineContext[Job]!!.invokeOnCompletion { cause ->
                if (cause is CancellationException) refreshListing()
            }
            _state.value = State.Preparing

            // Phase 1: collect all file tasks (expand folders recursively)
            val allTasks = mutableListOf<DownloadTask>()
            for (entry in entries) {
                val entryPath = "${currentPath.trimEnd('/')}/${entry.name}"
                if (entry.isDir) {
                    collectDirTasks(entryPath, "$downloadFolder${entry.name}/", allTasks)
                } else {
                    allTasks.add(DownloadTask(entryPath, entry.name, downloadFolder))
                }
            }

            if (allTasks.isEmpty()) {
                _opError.tryEmit(context.getString(R.string.sftp_nothing_to_download))
                refreshListing()
                return@launch
            }

            // Phase 2: detect conflicts and ask the user how to proceed
            val conflicting = allTasks.filter { findExistingDownload(it.filename, it.localDir) != null }
            var tasksToDownload: List<DownloadTask>
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
                        // Delete existing files so performDownload gets a clean slate
                        conflicting.forEach { task ->
                            findExistingDownload(task.filename, task.localDir)
                                ?.let { context.contentResolver.delete(it, null, null) }
                        }
                        tasksToDownload = allTasks
                    }
                }
            }

            if (tasksToDownload.isEmpty()) {
                refreshListing()
                return@launch
            }

            // Phase 3: download sequentially
            val total = tasksToDownload.size
            val skipped = allTasks.size - total
            for ((index, task) in tasksToDownload.withIndex()) {
                ensureActive()
                val ok = performDownload(null, task.filename, task.remotePath, task.localDir, index + 1, total)
                if (!ok) return@launch
            }

            _state.value = State.Downloaded(tasksToDownload.last().filename, total, skipped)
        }
    }

    fun cancelDownload() {
        batchDownloadJob?.cancel()
        batchDownloadJob = null
        // refreshListing() is called by invokeOnCompletion in downloadEntries
    }

    private suspend fun collectDirTasks(
        remotePath: String,
        localDir: String,
        tasks: MutableList<DownloadTask>,
    ) {
        runCatching {
            val entries = sftpSession!!.listDir(remotePath)
            for (entry in entries) {
                val entryPath = "$remotePath/${entry.name}"
                if (entry.isDir) {
                    collectDirTasks(entryPath, "$localDir${entry.name}/", tasks)
                } else {
                    tasks.add(DownloadTask(entryPath, entry.name, localDir))
                }
            }
        } // swallow per-subtree errors — remaining tasks continue
    }

    // ── Download helpers ──────────────────────────────────────────────────────

    fun dismissDownloaded() = refreshListing()

    private suspend fun performDownload(
        entry: SftpEntry?,
        filename: String,
        remotePath: String,
        localDir: String = downloadFolder,
        fileIndex: Int = 1,
        totalFiles: Int = 1,
    ): Boolean {
        val context = getApplication<Application>()
        val ext = filename.substringAfterLast('.', "").lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, localDir)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            _opError.tryEmit(context.getString(R.string.error_cannot_create_file))
            return false
        }
        // Detect if MediaStore silently renamed the file due to a filesystem collision
        // that slipped past findExistingDownload (e.g. stale MediaStore index).
        val actualFilename = context.contentResolver.query(
            uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null,
        )?.use { if (it.moveToFirst()) it.getString(0) else filename } ?: filename
        if (actualFilename != filename && entry != null) {
            // Collision still present: clean up the newly created entry and surface the conflict.
            context.contentResolver.delete(uri, null, null)
            val existingUri = findExistingDownload(filename, localDir)
            if (existingUri != null) {
                _conflictEvent.tryEmit(ConflictData(entry, remotePath, existingUri, localDir))
                return false
            }
            // existingUri is null (filesystem collision without a MediaStore record):
            // fall through and re-insert; the file will get a unique name automatically.
            val uri2 = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri2 == null) {
                _opError.tryEmit(context.getString(R.string.error_cannot_create_file))
                return false
            }
            val name2 = context.contentResolver.query(
                uri2, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null,
            )?.use { if (it.moveToFirst()) it.getString(0) else filename } ?: filename
            return doDownload(uri2, name2, remotePath, fileIndex, totalFiles)
        }
        return doDownload(uri, actualFilename, remotePath, fileIndex, totalFiles)
    }

    private suspend fun doDownload(
        uri: Uri,
        filename: String,
        remotePath: String,
        fileIndex: Int = 1,
        totalFiles: Int = 1,
    ): Boolean {
        val context = getApplication<Application>()
        _state.value = State.Downloading(filename, 0L, fileIndex, totalFiles)
        return runCatching {
            context.contentResolver.openOutputStream(uri)!!.use { out ->
                sftpSession!!.downloadFile(remotePath, out) { bytes ->
                    _state.value = State.Downloading(filename, bytes, fileIndex, totalFiles)
                }
            }
            true
        }.getOrElse {
            context.contentResolver.delete(uri, null, null)
            _opError.tryEmit(it.message ?: context.getString(R.string.error_download_failed))
            false
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
            _state.value = State.Uploaded(lastFilename, total)
        }
    }

    fun dismissUploaded() = refreshListing()

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
            }.onFailure { _opError.tryEmit(it.message ?: getApplication<Application>().getString(R.string.error_refresh_failed)) }
        }
    }

    // ── Auth callbacks ────────────────────────────────────────────────────────

    fun acceptHostKey() { hostKeyResult.tryEmit(true) }
    fun rejectHostKey() { hostKeyResult.tryEmit(false) }
    fun submitPassword(pwd: String) { passwordResult.tryEmit(pwd) }

    // ── Session lifecycle ─────────────────────────────────────────────────────

    /** Disconnects and removes the session from [SessionManager]. */
    fun disconnect() {
        batchDownloadJob?.cancel()
        batchDownloadJob = null
        deleteJob?.cancel()
        deleteJob = null
        val id = sessionId ?: return
        sftpSession = null
        pathStack.clear()
        sessionManager.remove(id)
        _state.value = State.Disconnected
        if (sessionManager.sessions.value.isEmpty()) {
            SshForegroundService.stop(getApplication())
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
