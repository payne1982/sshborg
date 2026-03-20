package com.sshborg.ui.sftp

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sshborg.SshBorgApp
import com.sshborg.data.db.HostEntity
import com.sshborg.data.ssh.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class SftpViewModel(app: Application) : AndroidViewModel(app) {

    sealed interface State {
        object Connecting : State
        data class HostKeyPrompt(val hostname: String, val fingerprint: String) : State
        data class PasswordPrompt(val hostname: String) : State
        data class Listing(val path: String, val entries: List<SftpEntry>) : State
        data class Downloading(val filename: String, val bytesReceived: Long) : State
        data class Downloaded(val filename: String) : State
        data class Uploading(val filename: String, val bytesSent: Long) : State
        data class Uploaded(val filename: String) : State
        data class Error(val message: String) : State
        object Disconnected : State
    }

    private val hostDao = (app as SshBorgApp).db.hostDao()
    private val keyDao  = (app as SshBorgApp).db.sshKeyDao()

    private val _state = MutableStateFlow<State>(State.Connecting)
    val state: StateFlow<State> = _state

    // Non-fatal operation errors (permission denied, download failed, etc.)
    // shown as a snackbar without leaving the current listing.
    private val _opError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val opError: SharedFlow<String> = _opError

    private var sftpSession: SftpSession? = null
    private val pathStack = mutableListOf<String>()

    private val hostKeyResult = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    private val passwordResult = MutableSharedFlow<String>(extraBufferCapacity = 1)

    fun connect(hostId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val host = hostDao.getById(hostId) ?: run {
                _state.value = State.Error("Host not found"); return@launch
            }
            val auth = buildAuth(host) ?: return@launch
            _state.value = State.Connecting

            val params = SshConnectionParams(
                hostname         = host.hostname,
                port             = host.port,
                username         = host.username,
                auth             = auth,
                agentForwarding  = false,
                knownHostsEntry  = host.knownHostsEntry,
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
                if (host.knownHostsEntry == null) {
                    hostDao.upsert(host.copy(knownHostsEntry = session.hostKeyLine))
                }
                navigateTo("/")
            }.onFailure { err ->
                _state.value = State.Error(err.message ?: "Connection failed")
            }
        }
    }

    fun navigateTo(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val entries = sftpSession!!.listDir(path)
                pathStack.add(path)
                _state.value = State.Listing(path, entries)
            }.onFailure {
                // Non-fatal: stay on current listing, show error as snackbar
                _opError.tryEmit(it.message ?: "Cannot list directory")
            }
        }
    }

    /** Navigates to parent directory. Returns false if already at root. */
    fun navigateUp(): Boolean {
        if (pathStack.size <= 1) return false
        pathStack.removeLast()
        val parent = pathStack.removeLast() // navigateTo will re-add it
        navigateTo(parent)
        return true
    }

    fun downloadFile(entry: SftpEntry, currentPath: String) {
        val remotePath = if (currentPath.endsWith("/")) "$currentPath${entry.name}"
                         else "$currentPath/${entry.name}"
        val context = getApplication<Application>()

        viewModelScope.launch(Dispatchers.IO) {
            _state.value = State.Downloading(entry.name, 0L)

            // Create the file in Downloads/SSHBorg/ via MediaStore (no permissions needed on API 30+)
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, entry.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/SSHBorg/")
            }
            val uri: Uri? = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            )
            if (uri == null) {
                _state.value = State.Error("Cannot create file in Downloads")
                return@launch
            }

            runCatching {
                context.contentResolver.openOutputStream(uri)!!.use { out ->
                    sftpSession!!.downloadFile(remotePath, out) { bytes ->
                        _state.value = State.Downloading(entry.name, bytes)
                    }
                }
                _state.value = State.Downloaded(entry.name)
            }.onFailure {
                // Remove the incomplete file, stay on listing
                context.contentResolver.delete(uri, null, null)
                dismissDownloaded()
                _opError.tryEmit(it.message ?: "Download failed")
            }
        }
    }

    /** Call after showing the Downloaded snackbar to return to the listing. */
    fun dismissDownloaded() = refreshListing()

    fun uploadFile(uri: Uri) {
        val context = getApplication<Application>()
        val currentPath = (state.value as? State.Listing)?.path ?: return

        // Resolve the display name from the URI
        val filename = context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment ?: "file"

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

    /** Call after showing the Uploaded snackbar to refresh and return to listing. */
    fun dismissUploaded() = refreshListing()

    private fun refreshListing() {
        val current = pathStack.lastOrNull() ?: return
        val session = sftpSession ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { _state.value = State.Listing(current, session.listDir(current)) }
        }
    }

    fun acceptHostKey() { hostKeyResult.tryEmit(true) }
    fun rejectHostKey() { hostKeyResult.tryEmit(false) }
    fun submitPassword(pwd: String) { passwordResult.tryEmit(pwd) }

    fun disconnect() {
        sftpSession?.disconnect()
        sftpSession = null
        pathStack.clear()
        _state.value = State.Disconnected
    }

    override fun onCleared() { super.onCleared(); disconnect() }

    private suspend fun buildAuth(host: HostEntity): SshAuth? {
        val keyPem = host.keyId?.let { keyDao.getById(it)?.privateKeyPem }
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
