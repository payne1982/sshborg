package com.sshborg.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The lockout schedule on the unlock screen, and the number of attempts that used to undo it. */
class AppLockBackoffTest {

    @Test fun `the schedule is the one that was always advertised`() {
        assertEquals(30L, AppLockManager.lockoutSeconds(1))
        assertEquals(60L, AppLockManager.lockoutSeconds(2))
        assertEquals(120L, AppLockManager.lockoutSeconds(3))
        assertEquals(240L, AppLockManager.lockoutSeconds(4))
        assertEquals(480L, AppLockManager.lockoutSeconds(5))
        assertEquals(900L, AppLockManager.lockoutSeconds(6))
        assertEquals("and then it stays there", 900L, AppLockManager.lockoutSeconds(7))
    }

    @Test fun `no number of failures shortens it`() {
        // `shl` on a Long uses only the low six bits of the count, so the 65th lockout shifted by
        // nothing and went back to 30 seconds, and the ones from the 60th on overflowed negative —
        // a lockout already in the past, which is no lockout at all. Walked over the whole range
        // because the fault was exactly that one value of n behaved differently from its
        // neighbours.
        var previous = 0L
        for (n in 1..200) {
            val secs = AppLockManager.lockoutSeconds(n)
            assertTrue("lockout $n must be positive, was $secs", secs > 0)
            assertTrue("lockout $n must not be shorter than $n-1", secs >= previous)
            assertTrue("lockout $n must not exceed the maximum, was $secs", secs <= 900L)
            previous = secs
        }
        assertEquals(900L, AppLockManager.lockoutSeconds(64))
        assertEquals(900L, AppLockManager.lockoutSeconds(65))
        assertEquals(900L, AppLockManager.lockoutSeconds(Int.MAX_VALUE))
    }

    @Test fun `a number below the first lockout is still a lockout`() {
        assertEquals(30L, AppLockManager.lockoutSeconds(0))
        assertEquals(30L, AppLockManager.lockoutSeconds(-1))
    }
}
