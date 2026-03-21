package com.sshborg.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sshborg.MainActivity
import com.sshborg.R
import com.sshborg.SshBorgApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
        val sessionManager = (application as SshBorgApp).sessionManager
        observeJob = scope.launch {
            sessionManager.sessions.collect { sessions ->
                updateNotification(sessions.size)
                if (sessions.isEmpty()) stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT_ALL) {
            (application as SshBorgApp).sessionManager.removeAll()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active SSH Sessions",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Shows active SSH/SFTP sessions" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(sessionCount: Int): Notification {
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
        val text = when (sessionCount) {
            0    -> "No active sessions"
            1    -> "1 active session"
            else -> "$sessionCount active sessions"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SSHBorg")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .addAction(0, "Disconnect all", disconnectAllIntent)
            .build()
    }

    private fun updateNotification(sessionCount: Int) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification(sessionCount))
    }

    companion object {
        private const val CHANNEL_ID = "ssh_sessions"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_DISCONNECT_ALL = "com.sshborg.DISCONNECT_ALL"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SshForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SshForegroundService::class.java))
        }
    }
}
