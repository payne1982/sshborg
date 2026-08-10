package com.sshborg.data.ssh

import android.util.Log
import com.jcraft.jsch.Logger
import com.sshborg.BuildConfig

/**
 * JSch logger with two jobs:
 *
 *  1. It keeps a small in-memory ring buffer of the most recent JSch messages. JSch records
 *     the real reason a connection ends (keepalive timeout, SSH_MSG_DISCONNECT from the server,
 *     socket reset, rekey failure…). The terminal reader loop only sees that reason when it is
 *     running; a session that drops while backgrounded loses it entirely. The ring lets the UI
 *     surface a real cause even for those background drops. Always on (also in release), because
 *     the cause shown to the user must not depend on the build type.
 *
 *  2. In *debug* builds only, it forwards every message to Logcat (tag [TAG]) so a disconnect
 *     can be diagnosed live with `adb logcat -s SshBorgJSch`. Release builds never write to
 *     Logcat — no protocol details leak into the system log.
 */
object SshDiagnostics : Logger {

    const val TAG = "SshBorgJSch"
    private const val MAX_LINES = 64

    private val ring = ArrayDeque<String>(MAX_LINES)

    override fun isEnabled(level: Int): Boolean = true

    override fun log(level: Int, message: String) {
        synchronized(ring) {
            if (ring.size >= MAX_LINES) ring.removeFirst()
            ring.addLast(message)
        }
        if (BuildConfig.DEBUG) {
            when (level) {
                Logger.DEBUG -> Log.d(TAG, message)
                Logger.INFO  -> Log.i(TAG, message)
                Logger.WARN  -> Log.w(TAG, message)
                Logger.ERROR,
                Logger.FATAL -> Log.e(TAG, message)
                else         -> Log.d(TAG, message)
            }
        }
    }

    /** The most recent JSch log lines (newest last), or null if nothing has been logged yet. */
    fun recentTail(maxLines: Int = 8): String? = synchronized(ring) {
        if (ring.isEmpty()) null
        else ring.toList().takeLast(maxLines).joinToString("\n")
    }
}
