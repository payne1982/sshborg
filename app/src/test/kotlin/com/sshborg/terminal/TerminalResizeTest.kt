package com.sshborg.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A resize that reports the shape the terminal already has must do nothing at all.
 *
 * The view measures itself whenever its **pixels** change — an inset, a bar sliding away, a tab
 * switch measuring from scratch — and most of those leave the cell count exactly where it was.
 * The work a resize does is not free: it rebuilds the screen arrays and resets the scrolling
 * region, so a measurement that changed nothing used to undo a region a full-screen program had
 * set. Over mosh it cost more than that, which is `MoshTerminalTransport.applyResize`.
 */
class TerminalResizeTest {

    private val emulator = TerminalEmulator(80, 24)

    private fun feed(text: String) = emulator.process(text.toByteArray(Charsets.ISO_8859_1))

    @Test fun `a resize to the same shape leaves the scrolling region alone`() {
        feed("\u001b[5;10r")            // DECSTBM: rows 5..10 are the region
        assertEquals(4, emulator.buffer.scrollTop)
        assertEquals(9, emulator.buffer.scrollBottom)

        emulator.resize(80, 24)

        assertEquals("the region survives a resize that is not one", 4, emulator.buffer.scrollTop)
        assertEquals(9, emulator.buffer.scrollBottom)
    }

    @Test fun `a real resize still resets it`() {
        feed("\u001b[5;10r")
        emulator.resize(80, 30)

        assertEquals(0, emulator.buffer.scrollTop)
        assertEquals("a terminal of a new shape has no region left", 29, emulator.buffer.scrollBottom)
    }

    @Test fun `a resize to the same shape keeps the screen and its history`() {
        feed("one\r\ntwo\r\nthree")
        repeat(30) { emulator.buffer.scrollUp() }
        val history = emulator.buffer.scrollbackSize
        assertTrue("there is history to keep", history > 0)

        emulator.resize(80, 24)

        assertEquals(history, emulator.buffer.scrollbackSize)
        assertEquals(80, emulator.buffer.columns)
        assertEquals(24, emulator.buffer.rows)
    }
}
