package com.sshborg.data.terminal

/**
 * How much output may be waiting for the screen at once, counted in bytes and in events.
 *
 * The queue it guards exists so the loop that carries a mosh session never waits for a draw (see
 * `MoshTerminalTransport.toScreen`), and it was unbounded. That is defensible for ordinary output
 * and not for a peer that means harm: mosh sends the *latest* state of a screen rather than a
 * stream of bytes, so real output does not pile up — each frame replaces the last. A backlog of
 * megabytes therefore does not mean a busy server, it means one sending faster than any screen
 * could ever be read, and the only thing growing is our heap.
 *
 * Both numbers are needed. Bytes alone would let a flood of tiny frames through; events alone
 * would let a few enormous ones through.
 *
 * The event being applied right now is still counted — it is released only once it is done with —
 * so the ceiling covers everything held, not just what is queued behind the consumer.
 *
 * Going over is **not** survivable by dropping something. The events share one queue in the order
 * the far end sent them, and "put the screen back as it was at 233, then draw this" only means
 * anything if both arrive and in that order; a screen missing a chunk is a screen quietly telling
 * the user something untrue. So [reserve] refuses, and refusing ends the session.
 */
internal class TerminalBacklog(
    private val maximumBytes: Int = MAX_BYTES,
    private val maximumEvents: Int = MAX_EVENTS,
) {

    private var bytes = 0
    private var events = 0

    /** Once over, stays over: the session is ending, and nothing more should be taken in. */
    private var exceeded = false

    /** What was held when it gave way, for the report. */
    @Volatile var reachedBytes = 0
        private set

    @Volatile var reachedEvents = 0
        private set

    @Synchronized fun reserve(count: Int): Boolean {
        reachedBytes = bytes
        reachedEvents = events
        if (exceeded || count < 0 || count > maximumBytes - bytes || events >= maximumEvents) {
            exceeded = true
            return false
        }
        bytes += count
        events++
        return true
    }

    @Synchronized fun release(count: Int) {
        bytes -= count.coerceIn(0, bytes)
        if (events > 0) events--
    }

    /** The numbers themselves, for the line a lost session leaves behind. */
    fun describe(): String = "backlog=$reachedBytes/${maximumBytes}B events=$reachedEvents/$maximumEvents"

    companion object {
        /**
         * Eight megabytes and a thousand events: far above anything a terminal that is being read
         * can be behind by, and far below anything that threatens a phone.
         */
        const val MAX_BYTES = 8 * 1024 * 1024
        const val MAX_EVENTS = 1024
    }
}
