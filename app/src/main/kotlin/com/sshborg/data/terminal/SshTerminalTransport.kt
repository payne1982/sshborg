package com.sshborg.data.terminal

import com.sshborg.data.ssh.SshDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The part of a live shell session that a transport touches.
 *
 * It exists so that the read loop below can be put under a test without a server, a socket or a
 * JSch object anywhere near it — which is what the ordering it has to guarantee needs, since the
 * fault it guards against only appears when two readers meet. `ShellSession` implements it and
 * is still what every caller passes.
 */
interface ShellChannel {
    val isConnected: Boolean
    val exitStatus: Int
    val inputStream: java.io.InputStream
    fun write(data: ByteArray)
    fun resize(columns: Int, rows: Int)
}

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
 *
 * ## One reader, and the wire's own order
 *
 * A reader cannot be stopped by cancelling the coroutine that started it. Cancellation is
 * cooperative and a blocking `read` on JSch's pipe is not a suspension point, so a cancelled
 * reader sits in that read until a byte arrives — and by then its bytes are off the wire and
 * have to go somewhere. Leaving the screen or switching tabs throws the ViewModel away and
 * starts another reader, so the two meet; and two threads reading one stream can apply what they
 * took in the wrong order, which no lock around the emulator can put right. Escapes torn in half
 * and a cursor left somewhere else is exactly what that looks like.
 *
 * So the loop is owned here rather than by whoever called [read]:
 *
 * - [loop] admits one reader at a time, and holds the door for the whole of a read *and* its
 *   application, which is what keeps the order of two chunks the order they arrived in;
 * - [attached] is where bytes go — looked up again after every read, never captured — so the
 *   chunk a departing reader already holds lands on the screen that is being drawn, instead of
 *   on one nobody is looking at any more;
 * - the incumbent sees it is no longer the one attached and leaves at the top of its next turn,
 *   handing the loop to whoever is waiting at the door.
 *
 * How a session *ended* is decided once and kept in [ended], so every reader that was ever
 * attached to it gets the same answer, whether it was still in the loop at the time or not.
 */
class SshTerminalTransport(private val session: ShellChannel) : TerminalTransport {

    /** Where bytes go: the last caller of [read], and never a closure the loop captured. */
    @Volatile private var attached: ((ByteArray, Int) -> Unit)? = null

    /** Admits one reader to the loop at a time, for a whole read and its application. */
    private val loop = Mutex()

    /** Decided once, by whichever reader was in the loop when it happened. */
    private val ended = CompletableDeferred<TerminalTransport.End>()

    override val isConnected: Boolean get() = session.isConnected

    override fun write(data: ByteArray) {
        session.write(data)
    }

    override fun resize(columns: Int, rows: Int) {
        session.resize(columns, rows)
    }

    override suspend fun read(onBytes: (ByteArray, Int) -> Unit): TerminalTransport.End {
        attached = onBytes
        loop.withLock {
            // Still ours by the time the door opened — a third reader may have arrived while we
            // waited — and there is still a session to read from.
            if (attached === onBytes && !ended.isCompleted) {
                pump(onBytes)?.let { ended.complete(it) }
            }
        }
        // Handing the loop over is not the end of anything, so a reader that handed it over waits
        // here for the answer the session will eventually have. A cancelled one leaves at once,
        // by the cancellation this throws, which is what a tab switch wants it to do.
        return ended.await()
    }

    /**
     * Reads until the session ends, or until somebody else is attached.
     *
     * @return how it ended, or null when it was handed over and ending is not ours to decide.
     */
    private suspend fun pump(mine: (ByteArray, Int) -> Unit): TerminalTransport.End? {
        val buffer = ByteArray(4096)
        try {
            while (session.isConnected) {
                val n = session.inputStream.read(buffer)
                SshDiagnostics.onRead(n)   // debug-only breadcrumb
                if (n < 0) return endOfStream()
                // Whoever is attached now, which may no longer be us: these bytes were taken off
                // the wire before the handover and belong on the screen all the same.
                (attached ?: mine)(buffer, n)
                if (attached !== mine) return null
            }
            // The loop ended because isConnected flipped without an EOF: unexpected.
            return endOfStream()
        } catch (e: CancellationException) {
            // Before the general catch, which would otherwise record a cancelled reader's exit
            // as the way the session ended — for every other reader as well, now that the answer
            // is kept in one place.
            throw e
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
