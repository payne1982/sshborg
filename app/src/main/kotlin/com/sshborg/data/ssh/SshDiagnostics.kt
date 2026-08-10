package com.sshborg.data.ssh

import android.util.Log
import com.jcraft.jsch.Logger
import com.sshborg.BuildConfig

/**
 * Captures JSch's internal diagnostics for two purposes:
 *
 *  1. [lastDisconnectReason] — the single reason string JSch last gave for a session's main loop
 *     ending (e.g. "Software caused connection abort", "Connection reset"). This is needed because
 *     when the underlying SSH *session* dies, JSch closes the shell *channel* and our reader loop
 *     merely sees EOF (read == -1), never the exception — so the real reason lives only here. We
 *     keep just that one clean line: no key-algorithm negotiation, no stack trace, no other-session
 *     noise. Reset at the start of each connection attempt to avoid showing a stale reason.
 *
 *  2. Logcat forwarding (tag [TAG]) in **debug builds only**, so a disconnect can be traced live
 *     with `adb logcat -s SshBorgJSch`. Release builds write nothing to Logcat.
 */
object SshDiagnostics : Logger {

    const val TAG = "SshBorgJSch"
    private const val REASON_PREFIX = "Caught an exception, leaving main loop due to "
    private const val CONNECT_PREFIX = "Connecting to "

    @Volatile
    private var lastReason: String? = null

    @Volatile
    private var lastDetail: String? = null

    // In release we still want the reason (an INFO-level line), but not the verbose DEBUG chatter
    // (algorithm lists etc.) — so JSch skips building those strings entirely. In debug, everything
    // is enabled so Logcat gets the full trace.
    override fun isEnabled(level: Int): Boolean = BuildConfig.DEBUG || level >= Logger.INFO

    override fun log(level: Int, message: String) {
        when {
            message.startsWith(CONNECT_PREFIX) -> { lastReason = null; lastDetail = null }
            message.startsWith(REASON_PREFIX) -> {
                // JSch's default 3-arg logger appends the exception's stack trace to this message,
                // so `body` is the reason on the first line followed (when present) by the stack.
                val body = message.removePrefix(REASON_PREFIX)
                lastReason = body.substringBefore('\n').trim().takeIf { it.isNotEmpty() }
                lastDetail = body.trim().takeIf { it.isNotEmpty() }
            }
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

    /**
     * The reason JSch last gave for a session's main loop ending (first line only, e.g. "Software
     * caused connection abort"), or null if none since the last connect. Safe to show/copy —
     * contains no keys, passwords, or algorithms.
     */
    fun lastDisconnectReason(): String? = lastReason

    /**
     * The fuller version of [lastDisconnectReason]: the reason plus JSch's exception stack trace
     * when it provided one. A single relevant error, still free of key/algorithm/other-session
     * noise. Suitable for the expandable, copyable detail section.
     */
    fun lastDisconnectDetail(): String? = lastDetail
}
