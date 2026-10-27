package com.sshborg.data.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.InputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Two readers meeting on one SSH session.
 *
 * They meet whenever the terminal screen is left and come back to, or a tab is switched: the
 * ViewModel is thrown away and its reader cancelled, but cancellation is cooperative and a
 * blocking read is not a suspension point, so the old reader is still sitting in a read when the
 * new one starts. What that cost, before the loop was owned by the transport, was the *order*:
 * two threads taking turns on one stream can apply what they took in either order, and a torn
 * escape sequence leaves the parser and the cursor somewhere the server never put them.
 *
 * So the properties here are the ones a terminal cannot do without — every byte, once, in the
 * order the wire had them — plus the number that says there is only ever one reader in the loop.
 */
class SshReaderHandoverTest {

    /**
     * A stream that blocks until something is put on it, exactly as JSch's channel pipe does,
     * and counts how many reads are waiting on it.
     */
    private class Wire : InputStream() {
        private val chunks = LinkedBlockingQueue<ByteArray>()
        val reads = AtomicInteger()

        fun send(text: String) = chunks.put(text.toByteArray())

        /** Lets whoever is in the loop out, which is how a test ends without hanging on it. */
        fun endOfFile() = chunks.put(ByteArray(0))

        override fun read(): Int = throw UnsupportedOperationException("the transport reads blocks")

        /**
         * Blocks like JSch's pipe, but not for ever: a read with nothing to read ends the stream
         * after [IDLE_EOF_MS]. That is what keeps a *failing* run from hanging — a reader that
         * should not exist cannot sit here holding the test open, it ends and lets the
         * assertions say so.
         */
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            reads.incrementAndGet()
            val chunk = chunks.poll(IDLE_EOF_MS, TimeUnit.MILLISECONDS) ?: return -1
            if (chunk.isEmpty()) return -1
            chunk.copyInto(b, off)
            return chunk.size
        }
    }

    private class Channel(override val inputStream: Wire) : ShellChannel {
        @Volatile override var isConnected = true
        override val exitStatus = -1
        override fun write(data: ByteArray) = Unit
        override fun resize(columns: Int, rows: Int) = Unit
    }

    private suspend fun until(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) fail("timed out waiting until $what")
            delay(5)
        }
    }

    @Test fun `a replaced reader hands over without losing or reordering a byte`() = runBlocking {
        val wire = Wire()
        val transport = SshTerminalTransport(Channel(wire))
        val applied = StringBuilder()
        val sinks = mutableListOf<String>()

        // The reader a tab switch is about to throw away, held in a read with nothing to read.
        val leaving = launch(Dispatchers.IO) {
            transport.read { b, n -> synchronized(applied) { applied.append(String(b, 0, n)); sinks += "leaving" } }
        }
        until("the first reader is in a read") { wire.reads.get() == 1 }

        // The one that arrives with the new ViewModel. It cannot enter the loop yet: the reader
        // it replaces is inside it, holding a read it took before anyone asked it to stop.
        launch(Dispatchers.IO) {
            transport.read { b, n -> synchronized(applied) { applied.append(String(b, 0, n)); sinks += "arriving" } }
        }
        leaving.cancel()
        // Slack for the newcomer to announce itself. Nothing below depends on it having done so:
        // the order is guaranteed either way, and only the *sink* of the first chunk can differ.
        delay(200)

        val wrote = listOf("ab", "cd", "ef", "gh", "ij", "kl", "mn", "op", "qr", "st")
        wrote.forEach { wire.send(it) }
        until("every chunk was applied") { synchronized(applied) { applied.length } == 20 }

        // Every byte, once, in the order the wire had them. This is what two readers broke.
        assertEquals(wrote.joinToString(""), synchronized(applied) { applied.toString() })

        // And the chunk that was already off the wire went to the screen somebody is drawing,
        // not to the one the cancelled ViewModel left behind.
        assertEquals("arriving", synchronized(applied) { sinks.first() })

        // The number: eleven reads for ten chunks is one reader waiting at the end. Two readers
        // would be waiting on two, which is how this went wrong in the first place.
        delay(200)
        assertEquals(11, wire.reads.get())
        assertTrue("the cancelled reader is still running", leaving.isCompleted)

        // And let the one still in the loop out, or this test would wait for it for ever.
        wire.endOfFile()
    }

    @Test fun `a cancelled reader with nobody to hand over to keeps the screen fed`() = runBlocking {
        val wire = Wire()
        val transport = SshTerminalTransport(Channel(wire))
        val applied = StringBuilder()

        val only = launch(Dispatchers.IO) {
            transport.read { b, n -> synchronized(applied) { applied.append(String(b, 0, n)) } }
        }
        until("the reader is in a read") { wire.reads.get() == 1 }
        only.cancel()

        // The same choice mosh makes: a session nobody is looking at still has its screen kept
        // up to date, because the emulator this writes into belongs to the session and not to
        // the ViewModel that went away. A tab switched back to finds the output already there.
        wire.send("while away")
        until("the bytes still arrive") { synchronized(applied) { applied.length } == 10 }
        assertEquals("while away", synchronized(applied) { applied.toString() })

        wire.endOfFile()
    }

    /**
     * Five readers in a row while the output never stops — a tab being switched back and forth
     * over a build log. Each handover happens with a chunk already off the wire, which is the
     * moment the order used to be lost, and this asks for all five of them at once.
     */
    @Test fun `readers coming and going during output never disturb the order`() = runBlocking {
        val wire = Wire()
        val transport = SshTerminalTransport(Channel(wire))
        val applied = StringBuilder()
        val wrote = StringBuilder()
        var previous: kotlinx.coroutines.Job? = null

        for (round in 1..5) {
            val arriving = launch(Dispatchers.IO) {
                transport.read { b, n -> synchronized(applied) { applied.append(String(b, 0, n)) } }
            }
            previous?.cancel()
            previous = arriving
            delay(100)
            for (chunk in 1..10) {
                val text = "r$round-$chunk."
                wrote.append(text)
                wire.send(text)
            }
            until("round $round was applied") {
                synchronized(applied) { applied.length } == wrote.length
            }
        }

        assertEquals(wrote.toString(), synchronized(applied) { applied.toString() })
        // Fifty chunks, and one reader waiting on the fifty-first.
        delay(200)
        assertEquals(51, wire.reads.get())
        wire.endOfFile()
    }

    private companion object {
        /** How long a read with nothing to read waits before calling it the end of the stream. */
        const val IDLE_EOF_MS = 3_000L
    }
}
