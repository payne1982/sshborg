package com.sshborg.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two timers behind a mosh session's patience, and the one invariant that matters: the client
 * gives up before the server does, whatever the setting says.
 */
class MoshPatienceTest {

    @Test fun `the default is an hour and the client gets five minutes less`() {
        assertEquals(60, MoshPatience.serverMinutes(MoshPatience.DEFAULT_MINUTES))
        assertEquals(3_600, MoshPatience.serverSeconds(MoshPatience.DEFAULT_MINUTES))
        assertEquals(55 * 60 * 1000L, MoshPatience.clientMillis(MoshPatience.DEFAULT_MINUTES))
    }

    @Test fun `an hour is the floor, not just the default`() {
        for (asked in listOf(Int.MIN_VALUE, -1, 0, 1, 30, 59)) {
            assertEquals("$asked should be lifted to the floor",
                MoshPatience.DEFAULT_MINUTES, MoshPatience.serverMinutes(asked))
        }
    }

    @Test fun `a day is the ceiling`() {
        assertEquals(MoshPatience.MAX_MINUTES, MoshPatience.serverMinutes(Int.MAX_VALUE))
        assertEquals(MoshPatience.MAX_MINUTES, MoshPatience.serverMinutes(48 * 60))
    }

    @Test fun `the client always gives up first, for every value anyone could store`() {
        val values = MoshPatience.CHOICES + listOf(Int.MIN_VALUE, -7, 0, 61, 90, 1439, Int.MAX_VALUE)
        for (stored in values) {
            val server = MoshPatience.serverSeconds(stored) * 1000L
            val client = MoshPatience.clientMillis(stored)
            assertTrue("client $client should be under server $server (stored $stored)", client < server)
            assertTrue("and still long enough to be worth having", client >= 55 * 60 * 1000L)
            assertEquals("the gap is exactly the margin",
                MoshPatience.CLIENT_MARGIN_MINUTES * 60 * 1000L, server - client)
        }
    }

    @Test fun `every choice offered in settings is one the floor and ceiling allow`() {
        for (minutes in MoshPatience.CHOICES) {
            assertEquals(minutes, MoshPatience.serverMinutes(minutes))
            assertEquals("whole hours, so the settings label can say hours", 0, minutes % 60)
        }
        assertEquals("the first choice is the default", MoshPatience.DEFAULT_MINUTES, MoshPatience.CHOICES.first())
        assertEquals("the last choice is the ceiling", MoshPatience.MAX_MINUTES, MoshPatience.CHOICES.last())
    }
}
