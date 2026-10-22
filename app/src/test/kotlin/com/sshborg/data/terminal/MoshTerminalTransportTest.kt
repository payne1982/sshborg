package com.sshborg.data.terminal

import com.sshborg.TestConfig
import com.sshborg.data.ssh.MoshUnavailable
import com.sshborg.data.ssh.ShellSession
import com.sshborg.data.ssh.SshAuth
import com.sshborg.data.ssh.SshConnectionParams
import com.sshborg.data.ssh.SshManager
import com.sshborg.terminal.TerminalEmulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Phase 6's second gate: a terminal driven over mosh through the same interface SSH uses.
 *
 * [com.sshborg.mosh.MoshTransport] was proved against a real server by `MoshSessionIntegrationTest`,
 * but with a loop written by the test. This is the loop the app itself will run — the sockets, the
 * port hopping, the goodbye, the clock — and the terminal above it is the real [TerminalEmulator],
 * reached only through [TerminalTransport]. There is no Android in either, which is what lets the
 * whole arrangement be gated from the JVM.
 *
 * The questions it ends on, none of which can be answered "nothing happened":
 *
 * - a command typed through [TerminalTransport.write] appears **on our screen**, not merely in a
 *   byte stream somewhere — so the frames, the emulator and the two sizes all agree;
 * - with the reader detached, as a tab switch leaves it, the screen **still keeps up**: the loop
 *   outlives the ViewModel, which is the one structural thing mosh forces on the app;
 * - after moving to a new local port the session carries on, because the server followed us —
 *   which is the roaming the feature exists for, and the mechanism behind the Wi-Fi being switched
 *   off on a real phone;
 * - `exit` ends it as [TerminalTransport.End.CleanExit], the way an SSH shell does, so the screen
 *   above can close itself without ever learning which transport it had.
 *
 * Skipped when `test.properties` names no server, and skipped rather than failed when that server
 * has no usable `mosh-server`: that is a fact about the machine, not about us.
 */
class MoshTerminalTransportTest {

    private val configured = TestConfig.sshServer()

    private val emulator = TerminalEmulator(COLUMNS, ROWS)

    @Test fun `a mosh session runs the terminal, changes its local port and exits cleanly`() = runBlocking {
        val ssh = login()
        val handover = try {
            ssh.startMoshServer(networkTimeoutSeconds = NETWORK_TIMEOUT)
        } catch (e: MoshUnavailable) {
            assumeTrue(
                "the test server has no usable mosh-server (${e.reason})",
                e.reason != MoshUnavailable.Reason.NotInstalled &&
                    e.reason != MoshUnavailable.Reason.LocaleNotUtf8,
            )
            throw e
        }

        // The sink of last resort is the session's own emulator, exactly as it will be in the app.
        val transport = MoshTerminalTransport(handover, silenceLimitMs = SILENCE_LIMIT, unattendedSink = ::toScreen)
        var reader: Deferred<TerminalTransport.End>? = null
        try {
            transport.start()
            assertTrue(
                "no datagram ever came back from port ${handover.port}: inbound UDP blocked, or a " +
                    "server bound to an address other than the one we resolved",
                transport.awaitFirstContact(),
            )
            assertTrue("the transport says it is not connected", transport.isConnected)
            assertEquals(
                "a session in contact must report no silence, or the screen would say it was lost",
                0, transport.outOfContactSeconds.value,
            )
            assertFalse(
                "a mosh session must not claim it survives being left unattended",
                transport.survivesUnattended,
            )

            reader = reading(transport)
            transport.resize(COLUMNS, ROWS)
            assertTrue("no prompt ever arrived; the screen is still blank", awaitScreen { it.isNotBlank() })

            // `%s` rather than the word itself, so the echo of the command line cannot satisfy
            // this: the only way the text can appear is the shell having run it.
            transport.write("printf 'mosh-%s\\n' typed-through-the-interface\r".toByteArray())
            assertTrue(
                "what was typed never came back:\n${screen()}",
                awaitScreen { "mosh-typed-through-the-interface" in it },
            )

            // ── the screen keeps up with nobody reading ──────────────────────────────
            // A tab switch cancels the reader and makes a new one later. Under SSH what arrives in
            // between waits in the socket; under mosh there is nowhere for it to wait, so it is
            // applied as it comes. This is the proof that it is.
            reader.cancel()
            transport.write("printf 'mosh-%s\\n' with-nobody-reading\r".toByteArray())
            assertTrue(
                "the screen stopped keeping up once the reader detached:\n${screen()}",
                awaitScreen { "mosh-with-nobody-reading" in it },
            )
            reader = reading(transport)

            // ── a new local port, which is what a change of network comes to ─────────
            val hopsBefore = transport.localPortHops
            transport.changeLocalPort()
            assertTrue("the local port never changed", awaitThat { transport.localPortHops > hopsBefore })
            // The port we have left is kept, and listened on, because what the server is sending
            // right now is still addressed to it: it has not heard from the new one yet.
            assertEquals(
                "the port we hopped away from should still be open and readable",
                2, transport.openLocalPorts,
            )
            transport.write("printf 'mosh-%s\\n' after-the-port-change\r".toByteArray())
            assertTrue(
                "the server did not follow us to the new local port:\n${screen()}",
                awaitScreen { "mosh-after-the-port-change" in it },
            )

            // ── a resize, which only the pty on the far side can confirm ─────────────
            synchronized(emulator) { emulator.resize(WIDER, TALLER) }
            transport.resize(WIDER, TALLER)
            transport.write("printf 'size=%sx%s\\n' \"$(tput cols)\" \"$(tput lines)\"\r".toByteArray())
            assertTrue(
                "the resize never reached the remote pty:\n${screen()}",
                awaitScreen { "size=${WIDER}x$TALLER" in it },
            )

            // ── and the shell exiting ends the session, rather than a timeout ────────
            transport.write("exit\r".toByteArray())
            val end = withTimeout(END_TIMEOUT) { reader.await() }
            assertEquals(
                "a shell that exited should end the session cleanly, as it does over SSH",
                TerminalTransport.End.CleanExit, end,
            )
            assertFalse("the transport still claims to be connected", transport.isConnected)
            assertEquals(
                "datagrams arrived from the server that we could not read",
                0, transport.undecipherableDatagrams,
            )
        } finally {
            reader?.cancel()
            transport.close()
            runCatching { ssh.disconnect() }
        }
    }

    /**
     * What a mouse-aware program's modes do on the way through mosh — which is not what we
     * assumed, and is why a scroll gesture inside tmux did nothing on a real device.
     *
     * `?1000h` and `?1006h` arrive: mosh does carry mouse reporting, so the terminal knows the
     * program wants to be sent wheel events. `?1049h` does **not**: mosh keeps the terminal on its
     * own side and sends us only what it looks like, so it switches to the alternate screen itself
     * and never says so. Our gate for "the wheel belongs to the program" was *alternate screen and
     * mouse reporting*, and the first half can never be true here.
     *
     * Hence [TerminalEmulator.hasAlternateScreen], and hence this test: the fix rests on both
     * halves of this being true of the real server, not on reading its source.
     */
    @Test fun `mosh carries mouse reporting but never the alternate screen`() = runBlocking {
        val ssh = login()
        val handover = try {
            ssh.startMoshServer(networkTimeoutSeconds = NETWORK_TIMEOUT)
        } catch (e: MoshUnavailable) {
            assumeTrue("the test server has no usable mosh-server (${e.reason})", false)
            throw e
        }
        val transport = MoshTerminalTransport(handover, unattendedSink = ::toScreen)
        try {
            transport.start()
            assumeTrue("no datagram came back", transport.awaitFirstContact())
            transport.resize(COLUMNS, ROWS)
            assertTrue("no prompt arrived", awaitScreen { it.isNotBlank() })

            // Exactly what tmux asks for when it turns the mouse on.
            transport.write("printf '\\033[?1049h\\033[?1000h\\033[?1006h'\r".toByteArray())
            assertTrue(
                "mouse reporting never reached us, so the wheel could never be sent anywhere",
                awaitThat { synchronized(emulator) { emulator.mouseReporting } },
            )
            // Given a moment, in case the alternate screen were merely late rather than absent.
            awaitThat(timeoutMs = 2_000) { synchronized(emulator) { emulator.altScreenActive } }
            assertFalse(
                "mosh forwarded the alternate screen after all — then the wheel gate can go back " +
                    "to asking about it, and TerminalEmulator.hasAlternateScreen can go away",
                synchronized(emulator) { emulator.altScreenActive },
            )

            // And so, with the flag the mosh glue sets, the wheel reaches the program.
            synchronized(emulator) {
                emulator.hasAlternateScreen = false
                assertTrue(
                    "a wheel notch would still have gone to our own scrollback, not to tmux",
                    emulator.wheelBelongsToApplication,
                )
            }
        } finally {
            transport.close()
            runCatching { ssh.disconnect() }
        }
    }

    // ── the terminal above it ───────────────────────────────────────────────────

    private fun toScreen(buffer: ByteArray, n: Int) {
        synchronized(emulator) { emulator.process(buffer, 0, n) }
    }

    private fun CoroutineScope.reading(transport: TerminalTransport): Deferred<TerminalTransport.End> =
        async(Dispatchers.IO) { transport.read(::toScreen) }

    /** The whole screen as text, which is what somebody would be looking at. */
    private fun screen(): String = synchronized(emulator) {
        (0 until emulator.buffer.rows).joinToString("\n") { emulator.buffer.getRowText(it) }
    }

    private suspend fun awaitScreen(timeoutMs: Long = SCREEN_TIMEOUT, what: (String) -> Boolean): Boolean =
        awaitThat(timeoutMs) { what(screen().trim()) }

    private suspend fun awaitThat(timeoutMs: Long = SCREEN_TIMEOUT, done: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (done()) return true
            delay(POLL_MS)
        }
        return done()
    }

    // ── getting there ───────────────────────────────────────────────────────────

    private fun login(): ShellSession {
        assumeTrue(TestConfig.NO_SERVER, configured != null)
        val s = configured!!
        val params = SshConnectionParams(
            hostname = s.host,
            port = s.port,
            username = s.user,
            auth = SshAuth.PublicKey(s.key("i_plain")),
        )
        return runBlocking { SshManager.openShell(params) { _, _, _ -> true } }
    }

    private companion object {
        const val COLUMNS = 100
        const val ROWS = 30
        const val WIDER = 120
        const val TALLER = 40

        /**
         * What `mosh-server` is told to tolerate of our silence: short, so that a server left
         * behind by a run that crashed goes away by itself instead of holding a shell.
         */
        const val NETWORK_TIMEOUT = 120

        /** And how long the client waits, which here only has to outlast the test itself. */
        const val SILENCE_LIMIT = 120_000L

        const val SCREEN_TIMEOUT = 15_000L
        const val END_TIMEOUT = 15_000L
        const val POLL_MS = 50L
    }
}
