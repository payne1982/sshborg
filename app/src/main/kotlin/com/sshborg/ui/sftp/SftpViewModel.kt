package com.sshborg.ui.sftp

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
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
        data class HostKeyPrompt(val hostname: String, val fingerprint: String) : State
        data class PasswordPrompt(val hostname: String) : State
        data class Listing(val path: String, val entries: List<SftpEntry>, val nonce: Long = 0L) : State
        data class Downloading(val filename: String, val bytesReceived: Long) : State
        data class Downloaded(val filename: String) : State
        data class Uploading(val filename: String, val bytesSent: Long) : State
        data class Uploaded(val filename: String) : State
        data class Error(val message: String) : State
        object Disconnected : State
    }

    private val sshBorgApp    = app as SshBorgApp
    private val sessionManager = sshBorgApp.sessionManager
    private val hostDao        = sshBorgApp.db.hostDao()
    private val keyDao         = sshBorgApp.db.sshKeyDao()

    private var sessionId: String? = null

    private val _state = MutableStateFlow<State>(State.Connecting)
    val state: StateFlow<State> = _state

    private val _opError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val opError: SharedFlow<String> = _opError

    /** Emitted when a file to be downloaded already exists in Downloads/SSHBorg/. */
    data class ConflictData(val entry: SftpEntry, val remotePath: String, val existingUri: Uri)
    private val _conflictEvent = MutableSharedFlow<ConflictData>(extraBufferCapacity = 1)
    val conflictEvent: SharedFlow<ConflictData> = _conflictEvent

    private var sftpSession: SftpSession? = null
    private val pathStack = mutableListOf<String>()

    private val hostKeyResult  = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    private val passwordResult = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /**
     * Attaches to an existing SFTP session in [SessionManager].
     * If already connected, restores the last-known path.
     */
    fun attach(id: String) {
        sessionId = id
        val session = sessionManager.get(id) ?: run {
            _state.value = State.Error("Session not found"); return
        }

        if (session.sftpSession != null) {
            sftpSession = session.sftpSession
            // Restore the directory we were in
            val path = session.sftpCurrentPath
            pathStack.clear()
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
                _state.value = State.Error("Host not found"); return@launch
            }
            val auth = buildAuth(host) ?: return@launch
            _state.value = State.Connecting

            val params = SshConnectionParams(
                hostname        = host.hostname,
                port            = host.port,
                username        = host.username,
                auth            = auth,
                agentForwarding = host.agentForwarding,
                knownHostsEntry = host.knownHostsEntry,
                jumpHosts       = parseJumpHosts(host.jumpHosts, host.jumpHostKeys),
            )

            runCatching {
                SshManager.openSftp(params) { hostname, fingerprint ->
                    runBlocking {
                        _state.value = State.HostKeyPrompt(hostname, fingerprint)
                        hostKeyResult.first()
                    }
                }
            }.onSuccess { session ->
                sftpSession = session
                sessionManager.update(id) { it.copy(sftpSession = session, status = SessionManager.Status.Connected) }

                if (host.knownHostsEntry == null)
                    hostDao.upsert(host.copy(knownHostsEntry = session.hostKeyLine))
                if (session.newJumpHostKeyLines.isNotEmpty()) {
                    val current = hostDao.getById(hostId) ?: host
                    val existing = current.jumpHostKeys?.lines()?.filter { it.isNotBlank() } ?: emptyList()
                    val merged = (existing + session.newJumpHostKeyLines).joinToString("\n")
                    hostDao.upsert(current.copy(jumpHostKeys = merged))
                }
                navigateTo(session.homePath)
            }.onFailure { err ->
                _state.value = State.Error(err.message ?: "Connection failed")
                sessionManager.update(id) { it.copy(status = SessionManager.Status.Error) }
            }
        }
    }

    fun navigateTo(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val entries = sftpSession!!.listDir(path)
                pathStack.add(path)
                sessionManager.update(sessionId ?: return@launch) { it.copy(sftpCurrentPath = path) }
                _state.value = State.Listing(path, entries)
            }.onFailure {
                _opError.tryEmit(it.message ?: "Cannot list directory")
            }
        }
    }

    fun navigateUp(): Boolean {
        if (pathStack.size <= 1) return false
        pathStack.removeLast()
        val parent = pathStack.removeLast()
        navigateTo(parent)
        return true
    }

    fun downloadFile(entry: SftpEntry, currentPath: String) {
        val remotePath = if (currentPath.endsWith("/")) "$currentPath${entry.name}"
                         else "$currentPath/${entry.name}"
        viewModelScope.launch(Dispatchers.IO) {
            val existing = findExistingDownload(entry.name)
            if (existing != null) {
                _conflictEvent.tryEmit(ConflictData(entry, remotePath, existing))
                return@launch
            }
            performDownload(entry.name, remotePath)
        }
    }

    fun downloadOverwrite(conflict: ConflictData) {
        viewModelScope.launch(Dispatchers.IO) {
            getApplication<Application>().contentResolver.delete(conflict.existingUri, null, null)
            performDownload(conflict.entry.name, conflict.remotePath)
        }
    }

    fun downloadKeepBoth(conflict: ConflictData) {
        viewModelScope.launch(Dispatchers.IO) {
            performDownload(conflict.entry.name, conflict.remotePath)
        }
    }

    private suspend fun performDownload(filename: String, remotePath: String) {
        val context = getApplication<Application>()
        _state.value = State.Downloading(filename, 0L)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/SSHBorg/")
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            _state.value = State.Error("Cannot create file in Downloads"); return
        }
        runCatching {
            context.contentResolver.openOutputStream(uri)!!.use { out ->
                sftpSession!!.downloadFile(remotePath, out) { bytes ->
                    _state.value = State.Downloading(filename, bytes)
                }
            }
            _state.value = State.Downloaded(filename)
        }.onFailure {
            context.contentResolver.delete(uri, null, null)
            dismissDownloaded()
            _opError.tryEmit(it.message ?: "Download failed")
        }
    }

    private fun findExistingDownload(filename: String): Uri? {
        val context = getApplication<Application>()
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND " +
                        "${MediaStore.Downloads.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(filename, "${Environment.DIRECTORY_DOWNLOADS}/SSHBorg/")
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

    fun dismissDownloaded() = refreshListing()

    fun uploadFile(uri: Uri) {
        val context = getApplication<Application>()
        val currentPath = (state.value as? State.Listing)?.path ?: return

        val filename = context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: uri.lastPathSegment ?: "file"

        val remotePath = "${currentPath.trimEnd('/')}/$filename"

        viewModelScope.launch(Dispatchers.IO) {
            _state.value = State.Uploading(filename, 0L)
            runCatching {
                context.contentResolver.openInputStream(uri)!!.use { stream ->
                    sftpSession!!.uploadFile(stream, remotePath) { bytes ->
                        _state.value = State.Uploading(filename, bytes)
                    }
                }
                _state.value = State.Uploaded(filename)
            }.onFailure {
                refreshListing()
                _opError.tryEmit(it.message ?: "Upload failed")
            }
        }
    }

    fun dismissUploaded() = refreshListing()

    fun deleteEntry(entry: SftpEntry, currentPath: String) {
        val path = "${currentPath.trimEnd('/')}/${entry.name}"
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (entry.isDir) sftpSession!!.deleteDir(path) else sftpSession!!.deleteFile(path)
                refreshListing()
            }.onFailure { _opError.tryEmit(it.message ?: "Delete failed") }
        }
    }

    fun renameEntry(entry: SftpEntry, currentPath: String, newName: String) {
        val oldPath = "${currentPath.trimEnd('/')}/${entry.name}"
        val newPath = "${currentPath.trimEnd('/')}/$newName"
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { sftpSession!!.rename(oldPath, newPath); refreshListing() }
                .onFailure { _opError.tryEmit(it.message ?: "Rename failed") }
        }
    }

    fun createDirectory(currentPath: String, name: String) {
        val path = "${currentPath.trimEnd('/')}/$name"
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { sftpSession!!.mkdir(path); refreshListing() }
                .onFailure { _opError.tryEmit(it.message ?: "Create directory failed") }
        }
    }

    fun refreshListing() {
        val current = pathStack.lastOrNull() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                _state.value = State.Listing(current, sftpSession!!.listDir(current), System.currentTimeMillis())
            }.onFailure { _opError.tryEmit(it.message ?: "Refresh failed") }
        }
    }

    fun acceptHostKey() { hostKeyResult.tryEmit(true) }
    fun rejectHostKey() { hostKeyResult.tryEmit(false) }
    fun submitPassword(pwd: String) { passwordResult.tryEmit(pwd) }

    /** Disconnects and removes the session from [SessionManager]. */
    fun disconnect() {
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
        // Don't disconnect — session stays alive in background
        // Clean up dangling connecting sessions
        val id = sessionId ?: return
        if (sessionManager.get(id)?.status == SessionManager.Status.Connecting) {
            sessionManager.remove(id)
            if (sessionManager.sessions.value.isEmpty()) {
                SshForegroundService.stop(getApplication())
            }
        }
    }

    private suspend fun buildAuth(host: HostEntity): SshAuth? {
        val keyPem = host.keyId?.let { id -> keyDao.getById(id)?.let { KeystoreManager.getPrivateKeyPem(it) } }
        return if (host.keyId != null && keyPem != null) {
            SshAuth.PublicKey(keyPem)
        } else {
            _state.value = State.PasswordPrompt(host.hostname)
            val pwd = passwordResult.first()
            if (pwd.isEmpty()) { _state.value = State.Disconnected; null }
            else SshAuth.Password(pwd)
        }
    }
}
