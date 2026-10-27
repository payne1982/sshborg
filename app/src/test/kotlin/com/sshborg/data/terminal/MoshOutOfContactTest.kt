package com.sshborg.data.terminal

import com.sshborg.data.ssh.MoshBootstrap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A session nobody answers: what the screen is told while it waits, and how it gives up.
 *
 * This needs no server — that is the point of it. Sending to a port with nothing behind it is
 * exactly the shape of the case this has to get right: a path that will not carry UDP, or a
 * network that has gone away, where the only symptom is silence. Nothing else tests the giving-up
 * path at all, and until the line on the screen existed there was nothing to test about the
 * waiting either.
 */
class MoshOutOfContactTest {

    private val applied = StringBuilder()

    @Test fun `silence is counted, shown in seconds, and eventually given up on`() = runBlocking {
        // A port in the range mosh uses, on this machine, with no mosh-server behind it. The key
        // is the lab's own, borrowed only for its shape: nothing here will ever decrypt anything.
        val nowhere = MoshBootstrap("127.0.0.1", 60999, com.sshborg.mosh.MoshVectors.SESSION_KEY)
        val transport = MoshTerminalTransport(
            nowhere,
            silenceLimitMs = SILENCE_LIMIT,
        ) { buffer, n -> applied.append(String(buffer, 0, n, Charsets.ISO_8859_1)) }

        val end = async(Dispatchers.IO) { transport.read { _, _ -> } }
        transport.start()

        assertFalse(
            "nothing can have answered, so there was no first contact",
            transport.awaitFirstContact(timeoutMs = SILENCE_LIMIT * 2),
        )
        // Counted from the start, not from the first answer: a session that never had contact is
        // out of contact, and the screen should say so rather than look merely quiet.
        assertTrue(
            "the silence should have been counted in whole seconds, not left at zero",
            transport.outOfContactSeconds.value >= 1,
        )

        val reason = withTimeoutOrNull(SILENCE_LIMIT * 3) { end.await() }
        assertTrue("the session should have been given up on, not left hanging", reason is TerminalTransport.End.Lost)
        val detail = (reason as TerminalTransport.End.Lost).detail.orEmpty()
        assertTrue("the report should say what the symptom was: $detail", "silent=" in detail)
        assertTrue("and what the limit was: $detail", "limit=" in detail)
        assertEquals("there was never a summary to give: the caller has the localised line", null, reason.summary)
        assertFalse("and it is no longer connected", transport.isConnected)
        assertEquals("nothing can have reached the screen", "", applied.toString())
        transport.close()
    }

    @Test fun `a stranger answering from the server's address is not contact`() = runBlocking {
        // A socket standing in for the server: it answers every datagram the client sends with
        // bytes that were not made with the session key. The source address is right, which is
        // all an attacker on the path has to manage, and it used to be all we checked — so the
        // session committed to mosh on the strength of it instead of falling back to SSH, and a
        // steady trickle of the same rubbish could then keep it from ever timing out.
        val stranger = java.nio.channels.DatagramChannel.open()
        stranger.bind(java.net.InetSocketAddress("127.0.0.1", 0))
        val port = (stranger.localAddress as java.net.InetSocketAddress).port
        val transport = MoshTerminalTransport(
            MoshBootstrap("127.0.0.1", port, com.sshborg.mosh.MoshVectors.SESSION_KEY),
            silenceLimitMs = SILENCE_LIMIT,
        ) { _, _ -> }

        val end = async(Dispatchers.IO) { transport.read { _, _ -> } }
        val answering = async(Dispatchers.IO) {
            val buffer = java.nio.ByteBuffer.allocate(2048)
            runCatching {
                while (true) {
                    buffer.clear()
                    val from = stranger.receive(buffer) ?: continue
                    stranger.send(java.nio.ByteBuffer.wrap(ByteArray(64) { 9 }), from)
                }
            }
        }
        transport.start()

        assertFalse(
            "rubbish from the right address is not an answer",
            transport.awaitFirstContact(timeoutMs = SILENCE_LIMIT / 2),
        )
        val reason = withTimeoutOrNull(SILENCE_LIMIT * 4) { end.await() }
        assertTrue(
            "a session nobody authentic is talking to must still time out",
            reason is TerminalTransport.End.Lost,
        )
        stranger.close()
        answering.cancel()
        transport.close()
    }

    @Test fun `nothing is counted before the loop is running`() = runBlocking {
        val nowhere = MoshBootstrap("127.0.0.1", 60998, com.sshborg.mosh.MoshVectors.SESSION_KEY)
        val transport = MoshTerminalTransport(nowhere, silenceLimitMs = SILENCE_LIMIT) { _, _ -> }
        try {
            // Before it starts there is nothing to count, and the screen shows no line. The other
            // half of this — the count going back to zero when contact returns — needs something
            // that answers, so it is asserted in MoshTerminalTransportTest against a real server.
            assertEquals(0, transport.outOfContactSeconds.value)
            transport.start()
            delay(1_400)
            assertTrue("once it is running, silence is counted", transport.outOfContactSeconds.value >= 1)
        } finally {
            transport.close()
        }
    }

    @Test fun `the family talked on is the family of the address, and it is said out loud`() {
        // Which family a session is on decides whether a firewall rule applies to it at all:
        // `mosh-server -s` binds to the address the SSH connection arrived on, so a rule written
        // for IPv4 does nothing for a session SSH made over IPv6. The dialog has to be able to say
        // which, because that is the one thing somebody needs in order to go and look — and the
        // socket has to be opened on the matching family, which the gate confirms end to end over
        // ::1 (`./gradlew test -PsshHost=::1`).
        val six = MoshTerminalTransport(
            MoshBootstrap("::1", 60123, com.sshborg.mosh.MoshVectors.SESSION_KEY),
        ) { _, _ -> }
        val four = MoshTerminalTransport(
            MoshBootstrap("127.0.0.1", 60124, com.sshborg.mosh.MoshVectors.SESSION_KEY),
        ) { _, _ -> }
        try {
            assertEquals("UDP port 60123 over IPv6", six.peerDescription)
            assertEquals("UDP port 60124 over IPv4", four.peerDescription)
        } finally {
            six.close()
            four.close()
        }
    }

    private companion object {
        /** Short, because the whole point here is to reach the end of patience quickly. */
        const val SILENCE_LIMIT = 4_000L
    }
}
