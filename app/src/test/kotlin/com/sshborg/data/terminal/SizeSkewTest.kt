package com.sshborg.data.terminal

import com.sshborg.TestConfig
import com.sshborg.data.ssh.ShellSession
import com.sshborg.data.ssh.SshAuth
import com.sshborg.data.ssh.SshConnectionParams
import com.sshborg.data.ssh.SshManager
import com.sshborg.terminal.TerminalBuffer
import com.sshborg.terminal.TerminalEmulator
import com.sshborg.terminal.TerminalScreenHistory
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * A frame painted for one shape of screen, landing on another — in both directions.
 *
 * The far end paints for the size it was last told. Open a session before the keyboard is up and
 * the size it is told is the tall one; by the time its first frame arrives the keyboard has taken
 * sixteen rows away, and a full repaint of 43 rows onto 27 scrolls the surplus off the bottom. It
 * was found on a phone — sixteen lines of prompt fragments and blanks at the very start of a
 * session nobody had typed into — and it reproduces here with the phone's own geometry.
 *
 * What the screen then shows is the far end's business and it corrects itself with the next frame.
 * What it must not do is leave those lines in the history, which is permanent and is what somebody
 * scrolls back into.
 *
 * The far end confirms a size by reporting its own, and that confirmation is what ends the skew —
 * but not here: it confirms the 43 it was told while this screen holds 27, and a shape we do not
 * have cannot lift a guard that exists because we do not have it. That is `ScreenPast.paintingFor`,
 * and this test is the reason it asks the screen instead of trusting the transport.
 *
 * The other direction is the second test, and it is the one that was wrong for two releases. A
 * screen that **grows** cannot have anything pushed off the bottom of it by a frame painted for a
 * shorter one, so there is nothing to swallow there — and the guard was swallowing the next
 * screenful of real output, which is a scrollback that looks broken. The keyboard going away is
 * that resize, and it happens as often as the keyboard arriving.
 */
class SizeSkewTest {

    // The phone's own geometry, from the log: 54 columns, 27 rows with the keyboard up.
    private val emulator = TerminalEmulator(54, 27)

    @Test fun `a repaint drawn for a taller screen leaves nothing in the history`() = runBlocking {
        val s = TestConfig.sshServer()
        assumeTrue(TestConfig.NO_SERVER, s != null)
        val ssh = login(s!!)
        val handover = ssh.startMoshServer(networkTimeoutSeconds = 120)
        val transport = MoshTerminalTransport(handover, past = pastFor(emulator)) { b, n ->
            synchronized(emulator) { emulator.process(b, 0, n) }
        }
        try {
            // What the app does: the keyboard is still down when the session is opened, so the
            // far end is told the tall size — and by the time its first frame arrives the
            // keyboard is up and our screen is 16 rows shorter.
            transport.resize(54, 43)
            transport.start()
            assumeTrue("no datagram came back", transport.awaitFirstContact())
            repeat(30) { delay(100) }
            synchronized(emulator) {
                assertEquals(
                    "sixteen rows of the far end's repaint must not have become our history",
                    0, emulator.buffer.scrollbackSize,
                )
                assertTrue(
                    "and the prompt should still have arrived",
                    (0 until emulator.buffer.rows).any { emulator.buffer.getRowText(it).contains("$") },
                )
            }
        } finally {
            transport.close()
            runCatching { ssh.disconnect() }
        }
    }

    @Test fun `a screen that grows keeps the output that follows`() = runBlocking {
        val s = TestConfig.sshServer()
        assumeTrue(TestConfig.NO_SERVER, s != null)
        // Starts shorter than it ends: the keyboard is up, and goes away while the session runs.
        val screen = TerminalEmulator(54, 20)
        val ssh = login(s!!)
        val handover = ssh.startMoshServer(networkTimeoutSeconds = 120)
        val transport = MoshTerminalTransport(handover, past = pastFor(screen)) { b, n ->
            synchronized(screen) { screen.process(b, 0, n) }
        }
        try {
            transport.resize(54, 20)
            transport.start()
            assumeTrue("no datagram came back", transport.awaitFirstContact())
            repeat(20) { delay(100) }

            // The keyboard going away, as the app does it: the screen grows, and the far end is
            // told. Nothing it has in flight can push a line off the bottom of a taller screen.
            synchronized(screen) { screen.resize(54, 27) }
            transport.resize(54, 27)
            repeat(10) { delay(100) }

            // Forty lines, each slow enough to be its own frame — mosh sends one every 20 ms at
            // the fastest — so the far end expresses them as scrolling rather than as a repaint,
            // which is the only way any of this can reach a scrollback at all.
            val loop = "i=0; while [ \$i -lt 40 ]; do i=\$((i+1)); echo line\$i; sleep 0.08; done\n"
            transport.write(loop.toByteArray())
            repeat(80) { delay(100) }

            synchronized(screen) {
                val arrived = (0 until screen.buffer.rows).any {
                    screen.buffer.getRowText(it).contains("line40")
                }
                assumeTrue("the loop did not finish on this server", arrived)
                // Forty lines on a screen of 27 means at least thirteen left it. Armed with a
                // whole screen — which is what every resize used to do — every one of them was
                // thrown away and the history stayed empty.
                assertTrue(
                    "the lines that left the screen are the history: ${screen.buffer.scrollbackSize}",
                    screen.buffer.scrollbackSize >= 10,
                )
                val history = (0 until screen.buffer.scrollbackSize)
                    .mapNotNull { screen.buffer.getScrollbackLine(it) }
                    .joinToString("\n") { lineText(it) }
                assertTrue("and they are the real ones, not blanks: $history", history.contains("line3"))
            }
        } finally {
            transport.close()
            runCatching { ssh.disconnect() }
        }
    }

    private suspend fun login(s: TestConfig.Server): ShellSession = SshManager.openShell(
        SshConnectionParams(
            hostname = s.host, port = s.port, username = s.user,
            auth = SshAuth.PublicKey(s.key("i_plain")),
        )
    ) { _, _, _, _ -> true }

    /** The same arrangement the app makes, which is what carries the rule being tested. */
    private fun pastFor(em: TerminalEmulator): ScreenPast {
        val history = TerminalScreenHistory(em)
        return object : ScreenPast {
            override val depth get() = history.depth
            override fun keep(state: Long) = synchronized(em) { history.keep(state) }
            override fun restore(state: Long) = synchronized(em) { history.restore(state) }
            override fun forget() = synchronized(em) { history.forget() }
            override fun ignoreRepaint(lines: Int) = synchronized(em) { history.ignoreRepaint(lines) }
            override fun paintingFor(rows: Int) = synchronized(em) { history.paintingFor(rows) }
        }
    }

    private fun lineText(cells: Array<TerminalBuffer.Cell>) = buildString {
        for (c in cells) if (!c.trailing) appendCodePoint(c.code)
    }.trimEnd()
}
