package com.sshborg.ui.terminal

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The handoff that carries `OSC 52` from the parser to the screen, and the thing it got wrong.
 *
 * The text arrives on the thread that applies host bytes, and the clipboard can only be written
 * where the screen is — and only if somebody is looking at it. So the ViewModel *offers* the text
 * with `tryEmit`, which must never suspend that thread, and the screen collects it only while
 * RESUMED. The rule being expressed is "an event nobody is there for is dropped rather than
 * queued", and the first attempt said that as a flow with no buffer at all.
 *
 * Which does the **opposite**, as the first test here measures: on a rendezvous
 * `MutableSharedFlow` — `replay = 0`, `extraBufferCapacity = 0` — delivery needs the emitter to
 * suspend, so `tryEmit` fails whenever anybody *is* subscribed, and succeeds when nobody is,
 * dropping the value. The clipboard was therefore written in exactly the case where nothing
 * should happen, and never in the one it was built for. It reached a phone that way: the window
 * title changed, proving the escape arrived and was parsed, and the clipboard never moved.
 *
 * One slot with `DROP_OLDEST` is the arrangement that means what was intended. A collector busy
 * showing the message is given the newest text rather than a backlog, nothing is kept for a
 * screen that is not there, and a waiting collector is actually handed the value.
 *
 * This tests the library rather than our code on purpose, the same way `getMimeEncoder(70)` had
 * to be: the whole fault was a belief about what `tryEmit` does.
 */
class ClipboardHandoffTest {

    @Test fun `a rendezvous flow refuses tryEmit precisely when somebody is listening`() = runBlocking {
        val flow = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 0)
        val seen = mutableListOf<String>()
        val job = launch { flow.collect { seen += it } }
        flow.subscriptionCount.first { it > 0 }

        assertFalse("this is the trap: a waiting collector is not enough", flow.tryEmit("x"))
        delay(100)
        assertEquals("and nothing arrives", emptyList<String>(), seen)
        job.cancel()
    }

    @Test fun `and accepts it when nobody is, which is where the value is lost`() = runBlocking {
        val flow = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 0)
        assertTrue("true here means dropped, not delivered", flow.tryEmit("x"))
    }

    @Test fun `one slot delivers to a collector that is waiting`() = runBlocking {
        val flow = MutableSharedFlow<String>(
            replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        val seen = mutableListOf<String>()
        val job = launch { flow.collect { seen += it } }
        flow.subscriptionCount.first { it > 0 }

        assertTrue(flow.tryEmit("hello"))
        delay(100)
        assertEquals(listOf("hello"), seen)
        job.cancel()
    }

    @Test fun `and keeps nothing for a screen that is not there`() = runBlocking {
        val flow = MutableSharedFlow<String>(
            replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        assertTrue("offered with nobody looking", flow.tryEmit("while in the background"))

        val seen = mutableListOf<String>()
        val job = launch { flow.collect { seen += it } }
        flow.subscriptionCount.first { it > 0 }
        delay(100)
        assertEquals("a session in the background must not change the clipboard later",
            emptyList<String>(), seen)
        job.cancel()
    }

    @Test fun `a collector that is busy gets the newest, not a queue`() = runBlocking {
        val flow = MutableSharedFlow<String>(
            replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        val seen = mutableListOf<String>()
        // A collector as slow as the message it shows: the snackbar stays for seconds.
        val job = launch { flow.collect { seen += it; delay(300) } }
        flow.subscriptionCount.first { it > 0 }

        flow.tryEmit("first")
        delay(50)
        flow.tryEmit("second")
        flow.tryEmit("third")
        delay(700)
        assertEquals("the backlog is one slot deep and the newest wins",
            listOf("first", "third"), seen)
        job.cancel()
    }
}
