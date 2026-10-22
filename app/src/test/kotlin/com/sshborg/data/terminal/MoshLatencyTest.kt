package com.sshborg.data.terminal

import com.sshborg.TestConfig
import com.sshborg.data.ssh.MoshUnavailable
import com.sshborg.data.ssh.ShellSession
import com.sshborg.data.ssh.SshAuth
import com.sshborg.data.ssh.SshConnectionParams
import com.sshborg.data.ssh.SshManager
import com.sshborg.terminal.TerminalEmulator
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * How long a keystroke takes to come back, measured rather than felt.
 *
 * Written because the first run on a real phone said typing lagged by seconds on a LAN, and the
 * correctness gate next door could not have noticed: it waits up to fifteen seconds for text to
 * appear, so it would pass just as happily at two seconds a character. A gate that ends on a
 * number is the only kind that can answer this one.
 *
 * It prints the distribution and the transport's own view of the session — the round-trip estimate
 * the frame rate is derived from, and how many frames were thrown away for being diffed against a
 * state we no longer hold, which is the price of keeping one terminal instead of mosh's queue of
 * them and the first suspect when typing drags.
 */
class MoshLatencyTest {

    private val configured = TestConfig.sshServer()
    private val emulator = TerminalEmulator(COLUMNS, ROWS)

    @Test fun `a keystroke comes back in a time worth printing`() = runBlocking {
        val ssh = login()
        val handover = try {
            ssh.startMoshServer(networkTimeoutSeconds = 120)
        } catch (e: MoshUnavailable) {
            assumeTrue("no usable mosh-server (${e.reason})", false)
            throw e
        }
        val transport = MoshTerminalTransport(handover, past = past(), unattendedSink = ::toScreen)
        var reader: Deferred<TerminalTransport.End>? = null
        try {
            transport.start()
            assumeTrue("no datagram came back", transport.awaitFirstContact())
            reader = async(Dispatchers.IO) { transport.read(::toScreen) }
            transport.resize(COLUMNS, ROWS)
            await { screen().isNotBlank() }

            // A prompt with nothing moving in it, so that what comes back is the echo and not a
            // clock or a git branch being redrawn.
            transport.write("PS1='> '; clear\r".toByteArray())
            await { screen().trim().endsWith(">") }

            val samples = ArrayList<Long>()
            for (i in 0 until SAMPLES) {
                val c = ('a' + (i % 26))
                val before = System.currentTimeMillis()
                transport.write(c.toString().toByteArray())
                val arrived = await(timeoutMs = 10_000) { screen().contains("> $c") }
                val took = System.currentTimeMillis() - before
                if (arrived) samples.add(took)
                // Clear the line for the next one, and let the screen settle so that the
                // measurement after it starts from a quiet terminal.
                transport.write("\u0015".toByteArray())
                await { !screen().contains("> $c") }
                delay(BETWEEN_SAMPLES)
            }

            val sorted = samples.sorted()
            println(
                "mosh keystroke echo over ${samples.size}/$SAMPLES samples: " +
                    "min ${sorted.first()}ms, median ${sorted[sorted.size / 2]}ms, " +
                    "p90 ${sorted[(sorted.size * 9) / 10]}ms, max ${sorted.last()}ms"
            )
            println(
                "the transport's own view: round trip ~${transport.roundTripMs}ms, " +
                    "stale frames ${transport.staleFrames}, went back ${history.returns} times " +
                    "(refused ${history.refused}), port hops ${transport.localPortHops}, " +
                    "undecipherable ${transport.undecipherableDatagrams}"
            )
            println("all samples: $samples")
        } finally {
            reader?.cancel()
            transport.close()
            runCatching { ssh.disconnect() }
        }
    }

    /**
     * The same measurement with a screen that is slow to draw, which is what a weak tablet is.
     *
     * The drawing holds the emulator's lock, and so does whatever applies the host's bytes to it.
     * If that is the protocol loop itself, then every frame the draw is holding the lock for is a
     * frame the loop is not sending an acknowledgement in — and the far end starts retransmitting
     * instead of advancing. This models a terminal redrawing at about 30 frames a second on a
     * device where a redraw costs 20 ms, and asks what a keystroke then costs.
     */
    @Test fun `a screen that is slow to draw does not slow the protocol`() = runBlocking {
        val ssh = login()
        val handover = try {
            ssh.startMoshServer(networkTimeoutSeconds = 120)
        } catch (e: MoshUnavailable) {
            assumeTrue("no usable mosh-server (${e.reason})", false)
            throw e
        }
        val transport = MoshTerminalTransport(handover, past = past(), unattendedSink = ::toScreen)
        var reader: Deferred<TerminalTransport.End>? = null
        val drawing = java.util.concurrent.atomic.AtomicBoolean(true)
        // How long the painter had to wait for the lock before it could draw at all: on a device
        // this is a frame not drawn, which is what "the character appears seconds later" is.
        var worstWait = 0L
        var draws = 0
        val painter = Thread {
            while (drawing.get()) {
                val asked = System.currentTimeMillis()
                synchronized(emulator) {
                    val waited = System.currentTimeMillis() - asked
                    if (waited > worstWait) worstWait = waited
                    draws++
                    Thread.sleep(DRAW_MS)
                }
                Thread.sleep(BETWEEN_DRAWS)
            }
        }
        try {
            transport.start()
            assumeTrue("no datagram came back", transport.awaitFirstContact())
            reader = async(Dispatchers.IO) { transport.read(::toScreen) }
            transport.resize(COLUMNS, ROWS)
            await { screen().isNotBlank() }
            transport.write("PS1='> '; clear\r".toByteArray())
            await { screen().trim().endsWith(">") }

            painter.start()
            val samples = ArrayList<Long>()
            for (i in 0 until SAMPLES) {
                val c = ('a' + (i % 26))
                synchronized(heard) { heard.setLength(0) }
                val before = System.currentTimeMillis()
                transport.write(c.toString().toByteArray())
                val arrived = await(timeoutMs = 10_000) { heardTail().contains(c) }
                if (arrived) samples.add(System.currentTimeMillis() - before)
                transport.write("\u0015".toByteArray())
                await { screen().trim().endsWith(">") }
                delay(BETWEEN_SAMPLES)
            }
            val sorted = samples.sorted()
            println(
                "with a slow screen, ${samples.size}/$SAMPLES samples: min ${sorted.first()}ms, " +
                    "median ${sorted[sorted.size / 2]}ms, p90 ${sorted[(sorted.size * 9) / 10]}ms, " +
                    "max ${sorted.last()}ms"
            )
            println(
                "the transport's own view: round trip ~${transport.roundTripMs}ms, " +
                    "stale frames ${transport.staleFrames}, went back ${history.returns} times"
            )
            println("the painter drew $draws times and waited at worst ${worstWait}ms for the lock")
            println("all samples: $samples")
        } finally {
            drawing.set(false)
            runCatching { painter.join(2_000) }
            reader?.cancel()
            transport.close()
            runCatching { ssh.disconnect() }
        }
    }

    /**
     * Everything the host has sent, kept outside the emulator.
     *
     * The first version of the slow-screen measurement watched the emulator, which meant taking
     * the same lock the painter holds — and Java's monitors are not fair, so what it measured was
     * partly its own starvation behind two other contenders. This sees the bytes as they arrive,
     * which is what the session's latency actually is.
     */
    private val heard = StringBuilder()

    /** The same arrangement the app makes: the screen's recent pasts, under the emulator's lock. */
    private val history = com.sshborg.terminal.TerminalScreenHistory(emulator)

    private fun past(): ScreenPast = object : ScreenPast {
        override val depth get() = history.depth
        override fun keep(state: Long) = synchronized(emulator) { history.keep(state) }
        override fun restore(state: Long) = synchronized(emulator) { history.restore(state) }
        override fun forget() = synchronized(emulator) { history.forget() }
    }

    private fun toScreen(buffer: ByteArray, n: Int) {
        synchronized(heard) { heard.append(String(buffer, 0, n, Charsets.ISO_8859_1)) }
        synchronized(emulator) { emulator.process(buffer, 0, n) }
    }

    private fun heardTail(characters: Int = 200): String =
        synchronized(heard) { heard.takeLast(characters).toString() }

    private fun screen(): String = synchronized(emulator) {
        (0 until emulator.buffer.rows).joinToString("\n") { emulator.buffer.getRowText(it) }
    }

    private suspend fun await(timeoutMs: Long = 15_000, done: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (done()) return true
            delay(2)
        }
        return done()
    }

    private fun login(): ShellSession {
        assumeTrue(TestConfig.NO_SERVER, configured != null)
        val s = configured!!
        return runBlocking {
            SshManager.openShell(
                SshConnectionParams(
                    hostname = s.host, port = s.port, username = s.user,
                    auth = SshAuth.PublicKey(s.key("i_plain")),
                )
            ) { _, _, _ -> true }
        }
    }

    private companion object {
        const val COLUMNS = 100
        const val ROWS = 30
        const val SAMPLES = 20
        const val BETWEEN_SAMPLES = 150L

        /** A redraw on a weak device, and the gap between two of them at about 30 a second. */
        const val DRAW_MS = 20L
        const val BETWEEN_DRAWS = 10L
    }
}
