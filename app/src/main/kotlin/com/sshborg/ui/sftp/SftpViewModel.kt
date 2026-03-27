package com.sshborg.ui.sftp

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
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
        data class HostKeyPrompt(val hostname: String, val fingerprint: String) : State
        data class PasswordPrompt(val hostname: String, val wrongPassword: Boolean = false) : State
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
            _state.value = State.Error(getApplication<Application>().getString(R.string.error_session_not_found)); return
        }

        if (session.sftpSession != null) {
            sftpSession = session.sftpSession
            // Mark as non-Connecting immediately so SftpScreen doesn't call connect()
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

            while (true) {
                _state.value = State.Connecting

                val result = runCatching {
                    SshManager.openSftp(
                        SshConnectionParams(
                            hostname        = host.hostname,
                            port            = host.port,
                            username        = host.username,
                            auth            = auth,
                            agentForwarding = host.agentForwarding,
                            knownHostsEntry = host.knownHostsEntry,
                            jumpHosts       = parseJumpHosts(host.jumpHosts, host.jumpHostKeys),
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
            val unique = uniqueFilename(conflict.entry.name)
            performDownload(unique, conflict.remotePath)
        }
    }

    /** Returns a filename like "file(1).txt" that does not yet exist in Downloads/SSHBorg/. */
    private fun uniqueFilename(original: String): String {
        val dot = original.lastIndexOf('.')
        val base = if (dot > 0) original.substring(0, dot) else original
        val ext  = if (dot > 0) original.substring(dot) else ""
        var counter = 1
        while (true) {
            val candidate = "$base($counter)$ext"
            if (findExistingDownload(candidate) == null) return candidate
            counter++
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
            _state.value = State.Error(context.getString(R.string.error_cannot_create_file)); return
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
            _opError.tryEmit(it.message ?: getApplication<Application>().getString(R.string.error_download_failed))
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
                _opError.tryEmit(it.message ?: getApplication<Application>().getString(R.string.error_upload_failed))
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
            }.onFailure { _opError.tryEmit(it.message ?: getApplication<Application>().getString(R.string.error_delete_failed)) }
        }
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
