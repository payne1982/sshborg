package com.sshborg.data.terminal

import com.sshborg.TestConfig
import com.sshborg.data.ssh.SshAuth
import com.sshborg.data.ssh.SshConnectionParams
import com.sshborg.data.ssh.SshManager
import com.sshborg.terminal.TerminalEmulator
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * A frame painted for a taller screen than the one it lands on.
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
 */
class SizeSkewTest {

    // The phone's own geometry, from the log: 54 columns, 27 rows with the keyboard up.
    private val emulator = TerminalEmulator(54, 27)

    @Test fun `a repaint drawn for a taller screen leaves nothing in the history`() = runBlocking {
        val s = TestConfig.sshServer()
        assumeTrue(TestConfig.NO_SERVER, s != null)
        val ssh = SshManager.openShell(
            SshConnectionParams(
                hostname = s!!.host, port = s.port, username = s.user,
                auth = SshAuth.PublicKey(s.key("i_plain")),
            )
        ) { _, _, _ -> true }
        val handover = ssh.startMoshServer(networkTimeoutSeconds = 120)
        // The same arrangement the app makes, which is what carries the rule being tested.
        val history = com.sshborg.terminal.TerminalScreenHistory(emulator)
        val past = object : ScreenPast {
            override val depth get() = history.depth
            override fun keep(state: Long) = synchronized(emulator) { history.keep(state) }
            override fun restore(state: Long) = synchronized(emulator) { history.restore(state) }
            override fun forget() = synchronized(emulator) { history.forget() }
            override fun ignoreRepaint(lines: Int) =
                synchronized(emulator) { history.ignoreRepaint(lines) }
        }
        val transport = MoshTerminalTransport(handover, past = past) { b, n ->
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
}
