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
 * Going over is **not** survivable by dropping something *here*. The events share one queue in the
 * order the far end sent them, and "put the screen back as it was at 233, then draw this" only
 * means anything if both arrive and in that order; a screen missing a chunk is a screen quietly
 * telling the user something untrue. So [reserve] refuses, and refusing ends the session.
 *
 * Which is why the ceiling is not where the pressure is answered. [isBehind] is: long before the
 * ceiling, the loop stops taking datagrams in at all, and a datagram never opened is — to mosh — a
 * packet that was lost. The protocol is built for that: our acknowledgement stays where it was,
 * the server keeps sending from the state we last confirmed, and the frame that eventually gets
 * through is a diff from there to the newest state. Nothing is lost, nothing is drawn out of
 * order, the session lives, and the work saved is every frame in between. The ceiling is left as
 * what it should be — a thing that does not happen.
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

    /**
     * Whether the screen is far enough behind that nothing more should be taken in.
     *
     * Two marks and not one: letting go while the queue is anywhere above the high mark, and
     * starting again only once it is back under the low one, keeps a loop that is right at the
     * edge from alternating between the two on every datagram.
     */
    @Synchronized fun isBehind(): Boolean {
        letGo = if (letGo) bytes > lowBytes || events > lowEvents
                else bytes > highBytes || events > highEvents
        return letGo
    }

    private var letGo = false
    private val highBytes = maximumBytes / 8
    private val highEvents = maximumEvents / 4
    private val lowBytes = highBytes / 2
    private val lowEvents = highEvents / 2

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
