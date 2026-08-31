package com.sshborg.data.ssh

import android.os.SystemClock
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
            message.startsWith(CONNECT_PREFIX) -> { lastReason = null; lastDetail = null; onConnect() }
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

    // ── Debug-only breadcrumb log ────────────────────────────────────────────
    // A small timestamped ring buffer of session events (network changes, writes that failed,
    // app-initiated disconnects, reads), dumped into the copyable error overlay in debug builds
    // so a remote tester can just copy the error instead of running `adb logcat`. Costs nothing
    // and writes nothing in release (every entry point is guarded by BuildConfig.DEBUG).

    private const val MAX_EVENTS = 80
    private val events = ArrayDeque<Pair<Long, String>>()   // (elapsedRealtime ms, message)

    @Volatile private var connectAtMs = 0L
    @Volatile private var lastReadAtMs = 0L
    @Volatile private var totalBytesRead = 0L
    @Volatile private var lastWriteError: String? = null
    @Volatile private var netState: String = "?"

    /** Record a transport/network state string from a ConnectivityManager callback (debug only). */
    fun setNet(state: String) {
        if (!BuildConfig.DEBUG) return
        if (state != netState) { netState = state; event("net → $state") } else netState = state
    }

    /** Append a timestamped breadcrumb (debug only). */
    fun event(msg: String) {
        if (!BuildConfig.DEBUG) return
        synchronized(events) {
            events.addLast(SystemClock.elapsedRealtime() to msg)
            while (events.size > MAX_EVENTS) events.removeFirst()
        }
        Log.d(TAG, "evt: $msg")
    }

    private fun onConnect() {
        if (!BuildConfig.DEBUG) return
        val now = SystemClock.elapsedRealtime()
        connectAtMs = now; lastReadAtMs = now; totalBytesRead = 0; lastWriteError = null
        synchronized(events) { events.clear() }
        event("connect")
    }

    /** Reader loop calls this on every successful read (debug only). */
    fun onRead(n: Int) {
        if (!BuildConfig.DEBUG || n <= 0) return
        lastReadAtMs = SystemClock.elapsedRealtime(); totalBytesRead += n
    }

    /** A write to the channel threw (debug only). */
    fun onWriteError(e: Throwable) {
        if (!BuildConfig.DEBUG) return
        lastWriteError = "${e.javaClass.simpleName}: ${e.message}"
        event("write EX: $lastWriteError")
    }

    /**
     * A snapshot of the recent session state + breadcrumbs, or null in release. Appended to the
     * copyable disconnect detail so we can tell an app-initiated close from a network/socket abort.
     */
    fun snapshot(): String? {
        if (!BuildConfig.DEBUG) return null
        val now = SystemClock.elapsedRealtime()
        val sb = StringBuilder("\n──── debug diagnostics ────\n")
        if (connectAtMs > 0L) {
            sb.append("uptime ${(now - connectAtMs) / 1000.0}s · bytesRead $totalBytesRead · ")
            sb.append("sinceLastRead ${now - lastReadAtMs}ms\n")
        }
        sb.append("net now: $netState\n")
        lastWriteError?.let { sb.append("last write error: $it\n") }
        synchronized(events) {
            if (events.isNotEmpty()) {
                sb.append("events (Δms before now):\n")
                for ((t, m) in events) sb.append("  -${now - t}ms  $m\n")
            }
        }
        return sb.toString()
    }
}
