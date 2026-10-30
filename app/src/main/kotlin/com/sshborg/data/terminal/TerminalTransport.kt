package com.sshborg.data.terminal

import kotlinx.coroutines.flow.StateFlow

/**
 * Where a terminal's bytes come from.
 *
 * The terminal above this — the emulator, the view, the scrollback, the selection, the extra-key
 * bar, the IME's word mode, the suggestions — must not learn which transport it has. That is not
 * only tidiness: all of it has been settled by use and by bug reports, and a transport has no
 * business touching any of it. The seam is this narrow because the bytes only ever crossed in
 * three places to begin with: the read loop, the write of a keystroke (or of the emulator's own
 * reply to a query), and a resize.
 *
 * Implementations: [SshTerminalTransport], which is the shell channel the app has always used,
 * and the mosh one, which runs a UDP state machine instead.
 */
interface TerminalTransport {

    /** False once the far end has gone, as far as this transport can tell without asking. */
    val isConnected: Boolean

    /** Keystrokes, and the emulator's own answers to device queries. */
    fun write(data: ByteArray)

    fun resize(columns: Int, rows: Int)

    /**
     * Reads until the session ends.
     *
     * Each run of bytes is handed to [onBytes] as a buffer and a length — not as a fresh
     * ByteArray, because this is the hottest path in the app and a terminal filling the screen
     * calls it thousands of times. The buffer is the transport's own and is reused, so [onBytes]
     * must consume it before returning.
     */
    suspend fun read(onBytes: (ByteArray, Int) -> Unit): End

    /**
     * Whether the screen is synchronised from the far side rather than streamed to us.
     *
     * False for SSH, where the bytes are the remote program's own output appended at the cursor.
     * The client may then write into its own terminal as well, and does: the first screenful is
     * kept clear of the retracting title bar by line feeds of our own, put in before the first
     * byte arrives.
     *
     * True for mosh, where the far end keeps the terminal and sends what it *looks like* — as
     * differences against the screen it believes we hold. Two things follow, and both were learnt
     * the hard way on a real device. Anything we write into the emulator ourselves makes that
     * belief false, and from then on every frame is computed against a screen we do not have:
     * observed as the typed line jumping back to the top of the window after a few words, which is
     * the server positioning the cursor for its own layout while ours sat three rows lower. And
     * there is no alternate screen to go by, because `?1049h` is handled over there and never
     * forwarded — see [com.sshborg.terminal.TerminalEmulator.hasAlternateScreen].
     *
     * So this is not a label for "is it mosh". It is the one fact the layers above need, and each
     * of them takes from it only what it needs.
     */
    val screenIsSynchronised: Boolean get() = false

    /**
     * Whole seconds since the far end was last heard from, 0 while in contact — or null for a
     * transport that cannot tell.
     *
     * SSH cannot tell, and that is not an oversight in this interface: TCP acknowledges and
     * retransmits below us, so silence on an SSH session is indistinguishable from a server with
     * nothing to say. A protocol that keeps its own session alive with a heartbeat knows exactly,
     * and owes the screen the number — a terminal that cannot be reached is otherwise a
     * photograph with nothing to explain it.
     */
    val outOfContactSeconds: StateFlow<Int>? get() = null

    /**
     * Whether the session survives being left unattended while the terminal is in the background.
     *
     * SSH does: nothing is read, and TCP holds what arrives until [read] resumes. A protocol that
     * keeps its own session alive with a heartbeat does not — stop answering and the far end
     * eventually concludes we are gone. The difference is real, so it is stated here rather than
     * assumed by the caller.
     */
    val survivesUnattended: Boolean get() = true

    /** Closes whatever this transport owns. Never the SSH session, which outlives it. */
    fun close() {}

    /** Why the session ended. */
    sealed interface End {
        /** The remote program exited of its own accord: the screen may close itself. */
        object CleanExit : End

        /**
         * @param summary short line shown under the title.
         * @param detail  the full technical cause for the expandable, copyable section, or null.
         */
        data class Lost(val summary: String?, val detail: String?) : End
    }
}
