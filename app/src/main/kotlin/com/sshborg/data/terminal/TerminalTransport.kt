package com.sshborg.data.terminal

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
 * and the mosh one, which runs a UDP state machine instead. See `docs/mosh-plan.md` §8 phase 5.
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
