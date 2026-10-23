package com.sshborg.data.terminal

/**
 * Somewhere to put the screen as it was, and to get it back.
 *
 * Only a synchronised screen needs it: the far end sends differences against the screen it
 * believes we hold, which is the newest state it has heard us acknowledge rather than the one we
 * are showing, so about half of what arrives on a link with any latency in it was computed against
 * a state we have already passed. Being able to go back is the difference between applying those
 * frames and waiting another round trip for each of them.
 *
 * Declared here rather than taken as the terminal's own class so that the transport still names
 * nothing above it: it is handed one of these the same way it is handed a sink for bytes.
 * `TerminalScreenHistory` is the implementation, and the glue puts the two together.
 *
 * Every method is called from the one thread that applies host bytes to the screen, and in the
 * order the far end sent things, which is what makes "put it back, then draw this on top" mean
 * anything at all.
 */
interface ScreenPast {

    /**
     * How many pasts are kept. The protocol loop keeps its own tally of state numbers in step with
     * this, because it has to answer *whether* a frame can be used before the screen's own thread
     * gets round to doing it.
     */
    val depth: Int

    /** Keeps the screen as it is now, as the state numbered [state]. */
    fun keep(state: Long)

    /** Puts the screen back as it was at [state]. */
    fun restore(state: Long)

    /** Throws the lot away: the screen has changed shape and none of it can be put back. */
    fun forget()

    /**
     * Says that up to [lines] lines about to leave the screen are the far end repainting rather
     * than history, and should not be kept.
     *
     * Called when the far end has just been told a new size and has not heard it yet: for about a
     * round trip its frames are still drawn for the old shape, and a full repaint of a taller
     * screen scrolls the surplus off the bottom of this one. Measured on a phone, where a session
     * opened before the keyboard came up was painted at 43 rows onto the 27 that were left, and
     * sixteen lines of somebody else's repaint became the first sixteen lines of the history.
     */
    fun ignoreRepaint(lines: Int)
}
