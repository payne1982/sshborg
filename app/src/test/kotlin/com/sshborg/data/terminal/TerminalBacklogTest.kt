package com.sshborg.data.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ceiling on output waiting for the screen: both numbers, and no way back once it is hit. */
class TerminalBacklogTest {

    @Test fun `what is applied and then released makes room again`() {
        val backlog = TerminalBacklog(maximumBytes = 100, maximumEvents = 4)
        assertTrue(backlog.reserve(60))
        assertTrue(backlog.reserve(40))
        assertFalse("there is no room for one more byte", backlog.reserve(1))
    }

    @Test fun `a flood of tiny events is stopped by the event count`() {
        val backlog = TerminalBacklog(maximumBytes = 1_000_000, maximumEvents = 3)
        assertTrue(backlog.reserve(1))
        assertTrue(backlog.reserve(1))
        assertTrue(backlog.reserve(1))
        assertFalse("three events is all it was given", backlog.reserve(1))
    }

    @Test fun `one enormous event is stopped by the byte count`() {
        val backlog = TerminalBacklog(maximumBytes = 100, maximumEvents = 1000)
        assertFalse(backlog.reserve(101))
    }

    @Test fun `the event being applied is still counted`() {
        // The point of counting it: a consumer that has taken an event and is in the middle of
        // drawing it is still holding those bytes, and a ceiling that forgot about them would be
        // a ceiling on the queue rather than on the memory.
        val backlog = TerminalBacklog(maximumBytes = 10, maximumEvents = 4)
        assertTrue(backlog.reserve(10))
        assertFalse(backlog.reserve(1))
    }

    @Test fun `once over, it stays over`() {
        // Because the session is ending. Letting the next event in would mean a screen that
        // carries on after a gap, which is the one outcome worse than a session that stops.
        val backlog = TerminalBacklog(maximumBytes = 10, maximumEvents = 4)
        assertFalse(backlog.reserve(11))
        backlog.release(0)
        assertFalse("not even for something that would fit", backlog.reserve(1))
    }

    @Test fun `releasing more than was held does not go negative`() {
        val backlog = TerminalBacklog(maximumBytes = 10, maximumEvents = 2)
        assertTrue(backlog.reserve(4))
        backlog.release(9)
        assertTrue(backlog.reserve(10))
    }

    @Test fun `the report says what was held and what the limits were`() {
        val backlog = TerminalBacklog(maximumBytes = 10, maximumEvents = 2)
        assertTrue(backlog.reserve(4))
        assertFalse(backlog.reserve(40))
        assertEquals("backlog=4/10B events=1/2", backlog.describe())
    }
}
