package com.sshborg.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import com.sshborg.MainActivity
import com.sshborg.R
import com.sshborg.SshBorgApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine

class SshForegroundService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var observeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(0))
        observeSessionCount()
    }

    private fun observeSessionCount() {
        val app = application as SshBorgApp
        observeJob = scope.launch {
            combine(app.sessionManager.sessions, app.transferManager.transfers) { sessions, transfers ->
                Pair(sessions.size, transfers.count { it.status == BackgroundTransfer.Status.Running })
            }.collect { (sessionCount, downloadCount) ->
                if (sessionCount == 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    updateNotification(sessionCount, downloadCount)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT_ALL) {
            val app = application as SshBorgApp
            saveSftpLastPaths(app)
            app.sessionManager.removeAll()
        }
        return START_NOT_STICKY
    }

    private fun saveSftpLastPaths(app: SshBorgApp) {
        val sftpSessions = app.sessionManager.sessions.value
            .filter { it.type == SessionManager.SessionType.Sftp }
        if (sftpSessions.isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            for (session in sftpSessions) {
                val lastPath = session.sftpCurrentPath
                if (lastPath.isBlank()) continue
                val host = app.db.hostDao().getById(session.hostId) ?: continue
                if (host.sftpStartMode == "last") {
                    app.db.hostDao().upsert(host.copy(sftpStartDir = lastPath))
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Returns a Context whose locale matches the per-app language chosen by the user. */
    private fun localizedContext(): Context {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return this
        val locale = locales[0] ?: return this
        val config = resources.configuration
        config.setLocale(locale)
        return createConfigurationContext(config)
    }

    private fun createNotificationChannel() {
        val ctx = localizedContext()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, ctx.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW)
                .apply { description = ctx.getString(R.string.notification_channel_description) }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_TRANSFERS, ctx.getString(R.string.notification_channel_transfers_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = ctx.getString(R.string.notification_channel_transfers_description) }
        )
    }

    private fun buildNotification(sessionCount: Int, downloadCount: Int = 0): Notification {
        val ctx = localizedContext()
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val disconnectAllIntent = PendingIntent.getService(
            this, 0,
            Intent(this, SshForegroundService::class.java).apply { action = ACTION_DISCONNECT_ALL },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (sessionCount == 0) {
            ctx.getString(R.string.notification_no_sessions)
        } else {
            ctx.resources.getQuantityString(R.plurals.notification_active_sessions, sessionCount, sessionCount)
        }
        val subText = if (downloadCount > 0)
            ctx.resources.getQuantityString(R.plurals.notification_bg_downloads, downloadCount, downloadCount)
        else null
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(ctx.getString(R.string.app_name))
            .setContentText(text)
            .apply { if (subText != null) setSubText(subText) }
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .addAction(0, ctx.getString(R.string.notification_disconnect_all), disconnectAllIntent)
            .build()
    }

    private fun updateNotification(sessionCount: Int, downloadCount: Int = 0) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification(sessionCount, downloadCount))
    }

    companion object {
        private const val CHANNEL_ID = "ssh_sessions"
        internal const val CHANNEL_ID_TRANSFERS = "transfers"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_ID_DOWNLOAD = 2
        private const val NOTIFICATION_ID_UPLOAD = 3
        private const val NOTIFICATION_ID_DOWNLOAD_ERROR = 4
        private const val ACTION_DISCONNECT_ALL = "com.sshborg.DISCONNECT_ALL"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SshForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SshForegroundService::class.java))
        }

        fun notifyDownloadComplete(context: Context, message: String, openUri: Uri? = null, mime: String? = null) {
            // Tap target follows the shared DownloadIntents rule: a single file opens in a viewer,
            // an APK or a multi-file batch opens the system Downloads screen. If nothing can handle
            // the file's type, Android shows the usual "no app" message — acceptable, same as any download.
            val intent = DownloadIntents.open(openUri, mime)
            val contentIntent = PendingIntent.getActivity(
                context, NOTIFICATION_ID_DOWNLOAD, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            postTransferNotification(context, context.getString(R.string.sftp_download_complete), message, NOTIFICATION_ID_DOWNLOAD, contentIntent)
        }

        fun notifyDownloadError(context: Context, filename: String) =
            postTransferNotification(
                context,
                context.getString(R.string.sftp_background_download_failed),
                filename,
                NOTIFICATION_ID_DOWNLOAD_ERROR,
            )

        fun notifyUploadComplete(context: Context, message: String) =
            postTransferNotification(context, context.getString(R.string.sftp_upload_complete), message, NOTIFICATION_ID_UPLOAD)

        private fun postTransferNotification(context: Context, title: String, text: String, notifId: Int, contentIntent: PendingIntent? = null) {
            val nm = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID_TRANSFERS, context.getString(R.string.notification_channel_transfers_name), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.notification_channel_transfers_description) }
            )
            if (!nm.areNotificationsEnabled()) return
            NotificationCompat.Builder(context, CHANNEL_ID_TRANSFERS)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))   // wrap long destination paths
                .setSmallIcon(R.drawable.ic_notification)
                .setAutoCancel(true)
                .apply { contentIntent?.let { setContentIntent(it) } }
                .build()
                .also { nm.notify(notifId, it) }
        }
    }
}
