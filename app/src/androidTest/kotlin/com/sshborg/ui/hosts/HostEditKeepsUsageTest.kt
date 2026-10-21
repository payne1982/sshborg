package com.sshborg.ui.hosts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sshborg.TestApp
import com.sshborg.data.db.HostEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Editing a host must not throw away what the app has learned about it.
 *
 * Three columns are not fields of this form — when it was last connected to, how many times, and
 * where the user dragged it in the manual order — and `upsert` replaces the whole row. Leaving
 * them out of the entity therefore did not mean "don't touch them", it meant "set them back to
 * the defaults": renaming a host reset its most-used counter, forgot its last connection and lost
 * its place. Two of the three list orders degraded quietly every time anybody edited anything.
 *
 * Instrumented because the thing with the bug in it is an `AndroidViewModel` over a real Room
 * database, and the point is to drive the same `save` the screen's button does rather than a
 * reimplementation of it.
 */
@RunWith(AndroidJUnit4::class)
class HostEditKeepsUsageTest {

    private var hostId: Long = 0

    @After fun cleanUp() = runBlocking {
        val dao = TestApp.app.db.hostDao()
        dao.getAllOnce().filter { it.label.startsWith(LABEL) }.forEach { dao.delete(it) }
    }

    @Test fun editingAHostKeepsItsPlaceAndItsCounters() = runBlocking {
        val dao = TestApp.app.db.hostDao()
        // A run killed before its @After leaves the host behind; clearing namesakes first makes
        // this self-healing rather than needing the app's data wiped.
        dao.getAllOnce().filter { it.label.startsWith(LABEL) }.forEach { dao.delete(it) }
        hostId = dao.upsert(
            HostEntity(
                label = LABEL,
                hostname = "10.0.0.9",
                username = "payne",
                lastConnected = LAST_CONNECTED,
                connectCount = CONNECT_COUNT,
                position = POSITION,
            ),
        )

        val vm = onTheMainThread { AddEditHostViewModel(TestApp.app) }
        vm.loadHost(hostId)
        assertTrue("the form never loaded the host", waitFor { vm.hostname.value == "10.0.0.9" })

        // The smallest edit there is, and the one somebody actually does: a new name.
        vm.label.value = RENAMED
        var saved = false
        onTheMainThread { vm.save { saved = true } }
        assertTrue("save never finished", waitFor { saved })

        val after = dao.getById(hostId)
        assertEquals("the rename itself did not happen", RENAMED, after?.label)
        assertEquals("the most-used counter was reset", CONNECT_COUNT, after?.connectCount)
        assertEquals("the last connection was forgotten", LAST_CONNECTED, after?.lastConnected)
        assertEquals("the manual position was lost", POSITION, after?.position)
    }

    /** The ViewModel's own work runs on the main thread; so does anything that starts it. */
    private fun <T> onTheMainThread(block: () -> T): T {
        var result: T? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun waitFor(done: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (done()) return true
            Thread.sleep(POLL_MS)
        }
        return done()
    }

    private companion object {
        const val LABEL = "usage-host"
        const val RENAMED = "usage-host-renamed"
        const val LAST_CONNECTED = 1_700_000_000_000L
        const val CONNECT_COUNT = 7
        const val POSITION = 3
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 50L
    }
}
