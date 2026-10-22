package com.sshborg.terminal

/**
 * A few of the screen's recent pasts, so that a frame computed against one of them can still be
 * used.
 *
 * Only a *synchronised* screen needs this — mosh's. There the far end keeps the terminal and sends
 * differences against the screen it believes we hold, which is the newest state it has heard us
 * acknowledge rather than the one we are actually showing. An acknowledgement takes half a round
 * trip, so at a hundred milliseconds of round trip and a frame every fifty, roughly half of what
 * it sends is computed against a state we have already passed. A client holding one screen can
 * only throw those away and wait another round trip, which is what was measured on a tablet as
 * half a second a keystroke; mosh itself holds a list of past screens and applies the frame to the
 * right one, and this is that list.
 *
 * It is not a terminal feature with a transport's name on it. What it offers is "give me the
 * visible screen as a value, and put it back", and nothing in the emulator consults it or behaves
 * differently because it exists. SSH simply never asks.
 *
 * ### What is in a snapshot, and what deliberately is not
 *
 * The cells, the wrap flags, the cursor, the pen, the scroll region, and how long the scrollback
 * was — everything a frame of escape sequences draws against. Not the modes: mouse reporting,
 * cursor-key mode and the rest are not part of the painting and are re-sent by the far end when
 * they change, so rolling them back would be wrong rather than merely wasteful. Not the alternate
 * screen either, which under a synchronised screen never arrives: `?1049h` is handled over there
 * and never forwarded.
 *
 * Not thread-safe, and not internally synchronised: every entry point must be called with the
 * emulator's own lock held, the same as anything else that touches the screen.
 */
class TerminalScreenHistory(
    private val emulator: TerminalEmulator,
    override val depth: Int = DEFAULT_DEPTH,
) : com.sshborg.data.terminal.ScreenPast {

    private class Shot(
        val cells: Array<Array<TerminalBuffer.Cell>>,
        val wrapped: BooleanArray,
        val cursorRow: Int,
        val cursorCol: Int,
        val cursorVisible: Boolean,
        val style: TextStyle,
        val scrollTop: Int,
        val scrollBottom: Int,
        val scrollback: Int,
        val columns: Int,
        val rows: Int,
    )

    /** Oldest first, which is the order they are forgotten in. */
    private val shots = LinkedHashMap<Long, Shot>()

    /** Copies of the screen taken, and how many of them have been used to go back. */
    var kept = 0
        private set
    var returns = 0
        private set

    /** Asked for a past that was not there, or not this shape. Should stay at zero. */
    var refused = 0
        private set

    override fun keep(state: Long) {
        val buffer = emulator.buffer
        shots[state] = Shot(
            cells = buffer.copyScreen(),
            wrapped = buffer.copyWrapFlags(),
            cursorRow = buffer.cursorRow,
            cursorCol = buffer.cursorCol,
            cursorVisible = buffer.cursorVisible,
            style = buffer.currentStyle,
            scrollTop = buffer.scrollTop,
            scrollBottom = buffer.scrollBottom,
            scrollback = buffer.scrollbackSize,
            columns = buffer.columns,
            rows = buffer.rows,
        )
        kept++
        while (shots.size > depth) shots.remove(shots.keys.first())
    }

    /** Whether [state] is one of the pasts still held, asked before the work is queued. */
    fun holds(state: Long): Boolean = shots.containsKey(state)

    /** Throws the lot away: after a resize nothing kept here describes this screen. */
    override fun forget() {
        shots.clear()
    }

    /**
     * Puts the screen back as it was at [state].
     *
     * Refusing is not supposed to be possible — the loop only asks for a state it has been told
     * about, and a resize throws the lot away through [forget] — so a refusal is counted rather
     * than merely shrugged at: it would mean the bytes about to be applied are being drawn on a
     * screen they were not computed against.
     */
    override fun restore(state: Long) {
        val shot = shots[state]
        val buffer = emulator.buffer
        if (shot == null || shot.columns != buffer.columns || shot.rows != buffer.rows) {
            refused++
            return
        }

        buffer.restoreScreen(shot.cells)
        buffer.restoreWrapFlags(shot.wrapped)
        buffer.cursorRow = shot.cursorRow
        buffer.cursorCol = shot.cursorCol
        buffer.cursorVisible = shot.cursorVisible
        buffer.currentStyle = shot.style
        buffer.scrollTop = shot.scrollTop
        buffer.scrollBottom = shot.scrollBottom
        // The lines that scrolled away since are about to scroll away again, carried by the frame
        // being replayed over this screen.
        buffer.dropNewestScrollback(buffer.scrollbackSize - shot.scrollback)

        // Everything newer than this never happened, so it is not a past to go back to.
        shots.keys.filter { it > state }.forEach { shots.remove(it) }
        returns++
    }

    companion object {
        /**
         * Eight, where mosh keeps rather more. The far end is behind by one or two states in
         * practice and by a handful when it has lost track altogether; past that it is cheaper to
         * drop the frame and let it start again than to hold screens nobody will ask for.
         */
        const val DEFAULT_DEPTH = 8
    }
}
