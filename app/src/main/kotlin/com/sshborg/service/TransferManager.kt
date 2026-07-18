package com.sshborg.service

import android.app.Application
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.sshborg.BuildConfig
import com.sshborg.R
import com.sshborg.data.ssh.SftpSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class TransferTask(
    val remotePath: String,
    val filename: String,
    val localDir: String,
)

data class BackgroundTransfer(
    val id: String,
    val sessionId: String,
    val filename: String,
    val fileIndex: Int = 1,
    val totalFiles: Int = 1,
    val bytesReceived: Long = 0L,
    val skippedFiles: Int = 0,
    val status: Status = Status.Running,
    /** Epoch millis when the transfer was enqueued. */
    val startedAt: Long = System.currentTimeMillis(),
    /** Epoch millis when the transfer reached a terminal state; null while running. */
    val completedAt: Long? = null,
) {
    enum class Status { Running, Done, Error, Cancelled }
}

class TransferManager(private val app: Application) {

    val downloadFolder =
        "${Environment.DIRECTORY_DOWNLOADS}/SSHBorg${if (BuildConfig.DEBUG) "-debug" else ""}/"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _transfers = MutableStateFlow<List<BackgroundTransfer>>(emptyList())
    val transfers: StateFlow<List<BackgroundTransfer>> = _transfers.asStateFlow()

    private val jobMap     = ConcurrentHashMap<String, Job>()
    private val channelMap = ConcurrentHashMap<String, com.jcraft.jsch.ChannelSftp>()

    /**
     * Enqueues one or more files for download. Returns a transfer ID that can be used to
     * observe progress (via [transfers]) or cancel the transfer.
     * All tasks share one secondary SFTP channel and are downloaded sequentially.
     */
    fun enqueue(
        sessionId: String,
        sftpSession: SftpSession,
        tasks: List<TransferTask>,
    ): String {
        require(tasks.isNotEmpty())
        val id = UUID.randomUUID().toString()
        _transfers.update { it + BackgroundTransfer(id, sessionId, tasks.first().filename, 1, tasks.size) }

        val job = scope.launch {
            val thisJob = coroutineContext[Job]!!
            var channel: com.jcraft.jsch.ChannelSftp? = null
            var skipped = 0
            try {
                channel = sftpSession.openBackgroundChannel()
                channelMap[id] = channel

                for ((index, task) in tasks.withIndex()) {
                    if (!thisJob.isActive) break
                    update(id) { it.copy(filename = task.filename, fileIndex = index + 1, bytesReceived = 0L) }

                    val ext  = task.filename.substringAfterLast('.', "").lowercase()
                    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, task.filename)
                        put(MediaStore.Downloads.MIME_TYPE, mime)
                        put(MediaStore.Downloads.RELATIVE_PATH, task.localDir)
                    }
                    val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri == null) { skipped++; continue }

                    try {
                        app.contentResolver.openOutputStream(uri)!!.use { out ->
                            var received = 0L
                            channel.get(task.remotePath, out, object : com.jcraft.jsch.SftpProgressMonitor {
                                override fun init(op: Int, src: String?, dest: String?, max: Long) {}
                                override fun count(count: Long): Boolean {
                                    if (!thisJob.isActive) return false
                                    received += count
                                    update(id) { it.copy(bytesReceived = received) }
                                    return true
                                }
                                override fun end() {}
                            })
                        }
                    } catch (e: Exception) {
                        app.contentResolver.delete(uri, null, null)
                        if (!thisJob.isActive || e is CancellationException) break
                        skipped++   // per-file error: skip and continue
                    }
                }

                if (thisJob.isActive) {
                    jobMap.remove(id)
                    update(id) { it.copy(status = BackgroundTransfer.Status.Done, skippedFiles = skipped, completedAt = System.currentTimeMillis()) }
                    val last = tasks.last()
                    val msg = when {
                        tasks.size == 1 -> app.getString(R.string.sftp_saved_to_downloads, "${last.localDir}${last.filename}")
                        skipped > 0     -> app.getString(R.string.sftp_downloaded_n_files_skipped, tasks.size - skipped, skipped)
                        else            -> app.getString(R.string.sftp_downloaded_n_files, tasks.size)
                    }
                    SshForegroundService.notifyDownloadComplete(app, msg)
                } else if (jobMap.remove(id) != null) {
                    update(id) { it.copy(status = BackgroundTransfer.Status.Cancelled, completedAt = System.currentTimeMillis()) }
                }
            } catch (e: Exception) {
                if (jobMap.remove(id) != null) {
                    val cancelled = e is CancellationException || !thisJob.isActive
                    update(id) { it.copy(status = if (cancelled) BackgroundTransfer.Status.Cancelled else BackgroundTransfer.Status.Error, completedAt = System.currentTimeMillis()) }
                    if (!cancelled) SshForegroundService.notifyDownloadError(app, tasks.firstOrNull()?.filename ?: "")
                }
            } finally {
                runCatching { channel?.disconnect() }
                channelMap.remove(id)
                jobMap.remove(id)
            }
        }
        jobMap[id] = job
        return id
    }

    /** Convenience overload for a single file. */
    fun enqueue(
        sessionId: String,
        sftpSession: SftpSession,
        remotePath: String,
        filename: String,
        localDir: String = downloadFolder,
    ): String = enqueue(sessionId, sftpSession, listOf(TransferTask(remotePath, filename, localDir)))

    fun cancel(id: String) {
        jobMap.remove(id)?.cancel()
        // Disconnect on the IO scope: channel.disconnect() writes an SSH packet, and a
        // network write on the main thread throws after JSch has already advanced its
        // cipher state, corrupting the whole SSH connection.
        channelMap.remove(id)?.let { ch -> scope.launch { runCatching { ch.disconnect() } } }
        _transfers.update { list ->
            list.map { if (it.id == id && it.status == BackgroundTransfer.Status.Running) it.copy(status = BackgroundTransfer.Status.Cancelled, completedAt = System.currentTimeMillis()) else it }
        }
    }

    fun cancelBySession(sessionId: String) {
        _transfers.value
            .filter { it.status == BackgroundTransfer.Status.Running && it.sessionId == sessionId }
            .forEach { cancel(it.id) }
    }

    fun dismiss(id: String) {
        if (jobMap.containsKey(id)) return
        _transfers.update { list -> list.filter { it.id != id } }
    }

    private fun update(id: String, block: (BackgroundTransfer) -> BackgroundTransfer) {
        _transfers.update { list -> list.map { if (it.id == id) block(it) else it } }
    }
}
