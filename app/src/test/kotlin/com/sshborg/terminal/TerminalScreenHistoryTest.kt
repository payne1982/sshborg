package com.sshborg.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Going back to an earlier screen, which is what lets a mosh frame computed against a state we
 * have passed be used instead of thrown away.
 *
 * The thing to hold it to is not "the cells are the same" but **"the screen is the screen it
 * was"**: the cursor where it stood, the pen it was writing with, and — the one that is easy to
 * get wrong — a scrollback no longer than it was, because the lines that scrolled away since are
 * about to scroll away again, carried by the frame replayed over the top. A history that left them
 * behind would quietly double every line of a long session's output.
 */
class TerminalScreenHistoryTest {

    private val emulator = TerminalEmulator(20, 4)
    private val history = TerminalScreenHistory(emulator)

    private fun screen() = (0 until emulator.buffer.rows)
        .joinToString("|") { emulator.buffer.getRowText(it) }

    @Test fun `the screen comes back as it was`() {
        emulator.process("one\r\n")
        history.keep(1)
        val asItWas = screen()
        val cursorRow = emulator.buffer.cursorRow
        val cursorCol = emulator.buffer.cursorCol

        emulator.process("two three\r\n")
        assertNotEquals("the test needs the screen to have moved on", asItWas, screen())

        history.restore(1)
        assertEquals(asItWas, screen())
        assertEquals("the cursor is part of the screen", cursorRow, emulator.buffer.cursorRow)
        assertEquals(cursorCol, emulator.buffer.cursorCol)
        assertEquals(0, history.refused)
        assertEquals(1, history.returns)
    }

    @Test fun `the pen comes back with it`() {
        // Bold and a colour, left set: the next frame is drawn with whatever the pen was.
        emulator.process("\u001b[1;31m")
        history.keep(1)
        val style = emulator.buffer.currentStyle
        emulator.process("\u001b[0mplain")
        assertNotEquals(style, emulator.buffer.currentStyle)

        history.restore(1)
        assertEquals(style, emulator.buffer.currentStyle)
    }

    @Test fun `the scrollback does not keep what is about to arrive again`() {
        // Four rows, so this scrolls: three lines go into the history.
        emulator.process("a\r\nb\r\nc\r\nd\r\n")
        val before = emulator.buffer.scrollbackSize
        history.keep(1)

        emulator.process("e\r\nf\r\n")
        assertEquals("the test needs lines to have scrolled away", before + 2, emulator.buffer.scrollbackSize)

        history.restore(1)
        assertEquals(
            "the lines that scrolled away since must not be left in the history twice",
            before, emulator.buffer.scrollbackSize,
        )
    }

    @Test fun `a screen of another shape cannot be put back`() {
        emulator.process("one")
        history.keep(1)
        emulator.resize(30, 6)

        history.restore(1)
        assertEquals("refused, and counted rather than shrugged at", 1, history.refused)
        assertEquals(0, history.returns)
    }

    @Test fun `only the last few pasts are kept`() {
        repeat(TerminalScreenHistory.DEFAULT_DEPTH + 3) { i ->
            emulator.process("line $i\r\n")
            history.keep(i.toLong())
        }
        // The oldest are gone, the newest are there. Asked through restore, which is the only
        // thing that can answer it.
        history.restore(0)
        assertEquals("a past that old should have been forgotten", 1, history.refused)
        history.restore((TerminalScreenHistory.DEFAULT_DEPTH + 2).toLong())
        assertEquals("the newest past must still be there", 1, history.returns)
    }

    @Test fun `going back forgets the future`() {
        emulator.process("one\r\n")
        history.keep(1)
        emulator.process("two\r\n")
        history.keep(2)

        history.restore(1)
        // State 2 never happened after that, so it is not somewhere to go back to.
        history.restore(2)
        assertEquals(1, history.returns)
        assertEquals(1, history.refused)
    }
}
