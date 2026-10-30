package com.sshborg.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How many lines of scrollback a terminal may be asked for.
 *
 * The number is not typed only into the settings field, where it is clamped: it also arrives in a
 * backup file, and a backup is not something anybody reads before applying it. It then decides an
 * allocation — `ArrayDeque(maxScrollback)` reserves that many slots the moment a terminal opens —
 * so a value nobody could type kills the app on the way to the first prompt.
 */
class ScrollbackSizeTest {

    @Test fun `a number out of all proportion is brought back into range`() {
        assertEquals(TerminalBuffer.MAX_SCROLLBACK, saneScrollback(2_000_000_000))
        assertEquals(TerminalBuffer.MAX_SCROLLBACK, saneScrollback(Int.MAX_VALUE))
        assertEquals(TerminalBuffer.MIN_SCROLLBACK, saneScrollback(0))
        assertEquals(TerminalBuffer.MIN_SCROLLBACK, saneScrollback(-5))
        assertEquals(2000, saneScrollback(2000))
    }

    @Test(timeout = 30_000) fun `a terminal asked for two billion lines opens anyway`() {
        // What the backup did. Two billion slots is sixteen gigabytes of references before a
        // single line has been printed, so this used to be an OutOfMemoryError in the terminal's
        // constructor — not a slow terminal, no terminal at all.
        val buffer = TerminalBuffer(80, 24, 2_000_000_000)
        assertEquals(TerminalBuffer.MAX_SCROLLBACK, buffer.maxScrollback)
        repeat(200) { buffer.scrollUp() }
        assertTrue("and it still keeps history", buffer.scrollbackSize > 0)
    }
}
