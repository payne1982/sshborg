package com.sshborg.data.terminal

import com.sshborg.data.ssh.ShellSession
import com.sshborg.data.ssh.SshDiagnostics
import kotlinx.coroutines.delay

/**
 * The transport the app has always had: the bytes of an SSH shell channel, read straight through.
 *
 * All of the reasoning about *how an SSH session ends* lives here, because every part of it is
 * specific to SSH and to JSch. There are three ways out of the loop and they do not agree with
 * each other, which is why this looks more careful than a read loop should:
 *
 * - **EOF.** The remote program exited, but the exit status may not have arrived yet, so the
 *   status is given a moment before deciding this was clean rather than a loss;
 * - **the session going away without EOF.** JSch closes the channel and the only trace of the
 *   reason is in its log, not in an exception, which is what [SshDiagnostics] is for;
 * - **an exception from `read`.** JSch may close the pipe through `disconnect()` instead of
 *   returning EOF, so an IOException here is a clean exit if the status is already set.
 */
class SshTerminalTransport(private val session: ShellSession) : TerminalTransport {

    override val isConnected: Boolean get() = session.isConnected

    override fun write(data: ByteArray) {
        session.write(data)
    }

    override fun resize(columns: Int, rows: Int) {
        session.resize(columns, rows)
    }

    override suspend fun read(onBytes: (ByteArray, Int) -> Unit): TerminalTransport.End {
        val buffer = ByteArray(4096)
        try {
            while (session.isConnected) {
                val n = session.inputStream.read(buffer)
                SshDiagnostics.onRead(n)   // debug-only breadcrumb
                if (n < 0) return endOfStream()
                onBytes(buffer, n)
            }
            // The loop ended because isConnected flipped without an EOF: unexpected.
            return endOfStream()
        } catch (e: Exception) {
            if (session.exitStatus != -1) return TerminalTransport.End.CleanExit
            // Just this exception's own stack: one relevant error, no cross-session log noise
            // and nothing about a key in it.
            return lost(
                e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName,
                e.stackTraceToString(),
            )
        }
    }

    private suspend fun endOfStream(): TerminalTransport.End {
        var waited = 0
        while (session.exitStatus == -1 && waited < EXIT_STATUS_GRACE) {
            delay(EXIT_STATUS_POLL.toLong())
            waited += EXIT_STATUS_POLL
        }
        if (session.exitStatus != -1) return TerminalTransport.End.CleanExit
        return lost(SshDiagnostics.lastDisconnectReason(), SshDiagnostics.lastDisconnectDetail())
    }

    /**
     * @param summary null when JSch had nothing to say; the caller supplies a generic line, since
     *   only it can reach the string resources.
     */
    private fun lost(summary: String?, detail: String?): TerminalTransport.End.Lost {
        // Debug builds append session and network breadcrumbs so a remote tester can copy the
        // error overlay instead of running adb logcat. No-op in release.
        val diagnostics = SshDiagnostics.snapshot()
        return TerminalTransport.End.Lost(
            summary = summary,
            detail = if (diagnostics != null) detail.orEmpty() + diagnostics else detail,
        )
    }

    private companion object {
        const val EXIT_STATUS_GRACE = 1000
        const val EXIT_STATUS_POLL = 50
    }
}
