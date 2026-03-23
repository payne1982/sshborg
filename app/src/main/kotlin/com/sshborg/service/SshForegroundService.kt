package com.sshborg.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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

class SshForegroundService : Service() {

    override fun attachBaseContext(base: Context) {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) {
            super.attachBaseContext(base)
        } else {
            val config = base.resources.configuration
            config.setLocales(android.os.LocaleList(locales[0]))
            super.attachBaseContext(base.createConfigurationContext(config))
        }
    }

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
                if (sessions.isEmpty()) {
                    // Must call stopForeground before stopSelf, otherwise the notification lingers
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                    stopSelf()
                } else {
                    updateNotification(sessions.size)
                }
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
        @Suppress("DEPRECATION")
        stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.notification_channel_description) }
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
        val text = if (sessionCount == 0) {
            getString(R.string.notification_no_sessions)
        } else {
            resources.getQuantityString(R.plurals.notification_active_sessions, sessionCount, sessionCount)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .addAction(0, getString(R.string.notification_disconnect_all), disconnectAllIntent)
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
