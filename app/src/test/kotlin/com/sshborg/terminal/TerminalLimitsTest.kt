package com.sshborg.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the terminal does with numbers and strings a well-behaved far end never sends.
 *
 * None of this needs a hostile server. An escape sequence reaches the parser from whatever is on
 * the far end's stdout, so `cat` of a file somebody else wrote is enough — and that makes these
 * the cheapest adversarial tests in the suite: the screen's lock is held while a sequence is
 * executed, so a count the parser takes at face value is the whole app stopping, not a wrong
 * character.
 *
 * Every test here has a **timeout**. That is the assertion in three of them: the old code did not
 * return at all, and a test that hangs reports nothing.
 */
class TerminalLimitsTest {

    private val emulator = TerminalEmulator(80, 24)

    private fun feed(text: String) = emulator.process(text.toByteArray(Charsets.ISO_8859_1))

    private fun screen() = (0 until emulator.buffer.rows)
        .joinToString("") { emulator.buffer.getRowText(it) }

    @Test(timeout = 20_000) fun `a repeat count of two billion repeats a screenful and stops`() {
        var title: String? = null
        emulator.onTitleChanged = { title = it }
        feed("x\u001b[2000000000b")
        // Nothing to assert about the drawing beyond this: what matters is that the call came
        // back. Unclamped it is two billion characters painted one at a time.
        assertTrue("the x and its repeats are on the screen", screen().contains("xx"))
        feed("\u001b]0;still here\u0007")
        assertEquals("still here", title)
    }

    @Test(timeout = 20_000) fun `a scroll of two billion lines blanks the region and stops`() {
        feed("one\r\ntwo\r\nthree")
        feed("\u001b[2000000000S")
        assertEquals("the region scrolled out of sight", "", screen().trim())
        assertTrue("and no more than the screen went into the scrollback",
            emulator.buffer.scrollbackSize <= emulator.buffer.rows + 3)
    }

    @Test(timeout = 20_000) fun `a parameter past what a number holds cannot turn negative`() {
        // 2147483648 is one past Int.MAX_VALUE: accumulated without saturating it becomes
        // Int.MIN_VALUE, and a negative count reaches operations that only bound theirs from
        // above. This one walked off the start of its own row.
        feed("hello\u001b[2147483648@")
        feed("\u001b[2147483648X")
        feed("\u001b[2147483648L")
        feed("\u001b[2147483648P")
        feed("\u001b[2147483648S")
        feed(" world")
        assertTrue("the terminal is still drawing: ${screen().trim()}", screen().contains("world"))
    }

    @Test fun `an OSC string that never ends is not kept, and does not become a title`() {
        var title: String? = null
        emulator.onTitleChanged = { title = it }
        feed("\u001b]0;" + "A".repeat(200_000) + "\u0007")
        assertNull("a title that long is not a title", title)
        // And the parser came back out of the string: the next one still works.
        feed("\u001b]0;short\u0007")
        assertEquals("short", title)
    }

    @Test(timeout = 20_000) fun `a stream of separators does not become a parameter list`() {
        feed("\u001b[" + ";".repeat(200_000) + "H")
        assertEquals("the sequence still ran, with its defaults", 0, emulator.buffer.cursorRow)
        assertEquals(0, emulator.buffer.cursorCol)
        feed("after")
        assertTrue(screen().startsWith("after"))
    }
}
