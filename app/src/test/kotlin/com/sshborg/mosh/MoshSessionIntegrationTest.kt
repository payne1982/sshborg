package com.sshborg.mosh

import com.sshborg.TestConfig
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * Phase 4's gate: a session with a real `mosh-server`, driven by the code the app would use.
 *
 * It skips itself unless a server has been started for it — `scripts/mosh-lab.sh` does that, runs
 * this, and takes the server down again. A green unit suite proves the pieces; only this proves
 * that mosh agrees with us about them.
 *
 * Three things are checked, in the one session, because they are only interesting together:
 * typing reaches a shell and its output comes back; the session survives being idle through
 * several heartbeats; and it survives **the local port being replaced underneath it**, which is
 * the mobile case the whole feature exists for — a phone moving from Wi-Fi to cellular gets a new
 * source address, and the sequence counter carrying on across that is what lets the server follow.
 */
class MoshSessionIntegrationTest {

    private val configured = TestConfig.moshServer()

    @Test fun `a session types, idles through heartbeats and survives a new local port`() {
        assumeTrue(TestConfig.NO_MOSH_SERVER, configured != null)
        val session = Session(configured!!)
        try {
            session.resize(80, 24)

            session.type("echo mosh-phase4-ok\r")
            assertTrue("the shell never answered", session.awaitOutput("mosh-phase4-ok", 10_000))

            // Nothing typed for longer than the heartbeat interval: the server must still be
            // talking to us afterwards, which it would not be if the heartbeat were not going out.
            session.idle(7_000)
            assertTrue("the server stopped answering while idle", session.heardFromServerWithin(4_000))

            // The phone changed network. Same session, same key, same sequence counter, new socket.
            session.rebindLocalPort()
            session.type("echo mosh-after-the-hop\r")
            assertTrue(
                "the session did not survive the change of local port",
                session.awaitOutput("mosh-after-the-hop", 15_000),
            )

            assertTrue("the closing handshake went unanswered", session.shutdown(5_000))
        } finally {
            session.close()
        }
    }

    /**
     * The smallest loop that can hold a session: tick, send what comes back, wait for a datagram
     * or for the next deadline, tick again. The real one belongs to phase 6, with the foreground
     * service and the Wi-Fi lock around it; this is here to prove the state machine, not to be
     * that.
     */
    private class Session(lab: TestConfig.Mosh) : AutoCloseable {

        private val address: InetAddress = InetAddress.getByName(lab.host)
        private val port = lab.port
        private var socket = DatagramSocket()
        private val output = StringBuilder()
        private var lastHeard = 0L

        private val transport = MoshTransport(
            MoshCrypto.fromPrintableKey(lab.key),
            object : MoshTransport.Listener {
                override fun onHostBytes(bytes: ByteArray) {
                    output.append(String(bytes, Charsets.ISO_8859_1))
                }
            },
        )

        private fun now(): Long = System.nanoTime() / 1_000_000

        fun type(text: String) = transport.type(text.toByteArray())

        fun resize(width: Int, height: Int) = transport.resize(width, height)

        /** Replaces the socket, as a change of network would, and keeps everything else. */
        fun rebindLocalPort() {
            socket.close()
            socket = DatagramSocket()
        }

        fun awaitOutput(text: String, timeoutMs: Long): Boolean =
            pumpUntil(timeoutMs) { output.contains(text) }

        fun heardFromServerWithin(timeoutMs: Long): Boolean {
            val before = lastHeard
            return pumpUntil(timeoutMs) { lastHeard > before }
        }

        fun idle(forMs: Long) {
            pumpUntil(forMs) { false }
        }

        fun shutdown(timeoutMs: Long): Boolean {
            transport.startShutdown(now())
            return pumpUntil(timeoutMs) { transport.shutdownAcknowledged || transport.shutdownTimedOut(now()) } &&
                transport.shutdownAcknowledged
        }

        private fun pumpUntil(timeoutMs: Long, done: () -> Boolean): Boolean {
            val deadline = now() + timeoutMs
            while (now() < deadline) {
                send(transport.tick(now()))
                if (done()) return true
                val wait = (transport.nextWakeup() - now()).coerceIn(1, 100)
                socket.soTimeout = wait.toInt()
                val buffer = ByteArray(2048)
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                    lastHeard = now()
                    transport.onDatagram(packet.data.copyOf(packet.length), now())
                    send(transport.tick(now()))
                } catch (_: SocketTimeoutException) {
                    // Nothing arrived in time, which is ordinary: tick again and wait some more.
                } catch (_: MoshProtocolException) {
                    // Someone else's packet, or a damaged one. Dropping it is the whole response.
                }
                if (done()) return true
            }
            return done()
        }

        private fun send(datagrams: List<ByteArray>) {
            for (datagram in datagrams) {
                socket.send(DatagramPacket(datagram, datagram.size, address, port))
            }
        }

        override fun close() = socket.close()
    }
}
