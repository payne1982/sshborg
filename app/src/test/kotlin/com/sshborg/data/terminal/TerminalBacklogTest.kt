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

    @Test fun `being behind starts above the high mark and ends below the low one`() {
        // 1024 bytes / 64 events: the marks are an eighth and a quarter of those, and the point
        // of the second mark is that a loop sitting exactly at the edge does not flap between
        // taking datagrams in and letting them go on every single one.
        val backlog = TerminalBacklog(maximumBytes = 1024, maximumEvents = 64)
        assertFalse("an empty queue is not behind", backlog.isBehind())
        repeat(16) { backlog.reserve(8) }          // 128 bytes, the high mark exactly
        assertFalse("at the mark is not past it", backlog.isBehind())
        backlog.reserve(8)
        assertTrue("past the mark it lets go", backlog.isBehind())
        repeat(8) { backlog.release(8) }           // down to 72, above the low mark
        assertTrue("and goes on letting go until it is well clear", backlog.isBehind())
        repeat(9) { backlog.release(8) }           // down to 0
        assertFalse("clear", backlog.isBehind())
    }

    @Test fun `too many events is being behind too, however small they are`() {
        val backlog = TerminalBacklog(maximumBytes = 1_000_000, maximumEvents = 64)
        repeat(17) { backlog.reserve(0) }
        assertTrue(backlog.isBehind())
    }
}
