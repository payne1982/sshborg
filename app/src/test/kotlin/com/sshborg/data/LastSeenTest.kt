package com.sshborg.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The arithmetic behind the "last connected" line under a host row.
 *
 * Every case fixes both ends of the clock, because a test that asks the system what time it is is a
 * test that passes at a different minute of the day.
 */
class LastSeenTest {

    private val rome = ZoneId.of("Europe/Rome")

    private fun at(
        year: Int, month: Int, day: Int, hour: Int, minute: Int = 0,
    ): Long = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, rome).toInstant().toEpochMilli()

    private fun seen(then: Long, now: Long) = lastSeen(then, now, rome)

    @Test fun `a few seconds is just now`() {
        val now = at(2026, 11, 4, 12, 0)
        assertEquals(LastSeen.JustNow, seen(now - 59_000, now))
    }

    @Test fun `the minute boundary is a minute, not nothing`() {
        val now = at(2026, 11, 4, 12, 0)
        assertEquals(LastSeen.Minutes(1), seen(now - 60_000, now))
        assertEquals(LastSeen.Minutes(59), seen(now - 59 * 60_000L, now))
    }

    @Test fun `an hour is hours, up to the day`() {
        val now = at(2026, 11, 4, 12, 0)
        assertEquals(LastSeen.Hours(1), seen(now - 3_600_000, now))
        assertEquals(LastSeen.Hours(23), seen(now - 23 * 3_600_000L, now))
    }

    @Test fun `late last night is still said in hours`() {
        // Half past midnight, three and a half hours after the connection. "Yesterday" would be
        // true of the date and useless as an answer.
        assertEquals(
            LastSeen.Hours(3),
            seen(at(2026, 11, 3, 21, 0), at(2026, 11, 4, 0, 30)),
        )
    }

    @Test fun `a day and a bit is yesterday`() {
        assertEquals(
            LastSeen.Yesterday,
            seen(at(2026, 11, 3, 0, 0), at(2026, 11, 4, 0, 30)),
        )
    }

    @Test fun `two to six calendar days are counted`() {
        val now = at(2026, 11, 4, 12, 0)
        assertEquals(LastSeen.Days(2), seen(at(2026, 11, 2, 12, 0), now))
        assertEquals(LastSeen.Days(6), seen(at(2026, 10, 29, 12, 0), now))
    }

    @Test fun `a week back is a date, not a count`() {
        val now = at(2026, 11, 4, 12, 0)
        assertEquals(LastSeen.Earlier, seen(at(2026, 10, 28, 12, 0), now))
        assertEquals(LastSeen.Earlier, seen(at(2025, 1, 1, 12, 0), now))
    }

    @Test fun `a timestamp in the future is just now, never a negative count`() {
        // A backup restored from a device whose clock was ahead.
        val now = at(2026, 11, 4, 12, 0)
        assertEquals(LastSeen.JustNow, seen(now + 60 * 60_000L, now))
        assertEquals(LastSeen.JustNow, seen(at(2027, 1, 1, 0, 0), now))
    }

    @Test fun `the epoch does not throw`() {
        // What a host whose lastConnected was written as 0 by some older import would look like.
        assertEquals(LastSeen.Earlier, seen(0L, at(2026, 11, 4, 12, 0)))
    }

    @Test fun `the same two instants are a different number of days in another zone`() {
        // 26 hours apart, and both ends sit near midnight: in Rome that crosses two dates, two
        // hours further west it crosses one. Which is the point of counting dates rather than
        // hours — the answer belongs to the calendar the person is reading, so the zone is a
        // parameter and not an assumption.
        val then = at(2026, 11, 2, 23, 0)
        val now = at(2026, 11, 4, 1, 0)
        assertEquals(LastSeen.Days(2), lastSeen(then, now, rome))
        assertEquals(LastSeen.Yesterday, lastSeen(then, now, ZoneId.of("Atlantic/Azores")))
    }
}
