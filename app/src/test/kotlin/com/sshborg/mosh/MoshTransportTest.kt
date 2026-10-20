package com.sshborg.mosh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The transport state machine, driven by a clock the test owns.
 *
 * Timing is the part of a protocol that cannot be checked by looking at it: a heartbeat that never
 * fires and a retransmission that fires forever both look reasonable in the source. So the
 * transport takes the time as an argument and hands back datagrams, and everything below happens
 * in a few microseconds of wall clock at whatever simulated times the test chooses.
 *
 * The other half of the arrangement is a hand-built server: these tests mint datagrams with the
 * server's direction bit, which is also the only way to exercise what we do with what arrives.
 */
class MoshTransportTest {

    private val crypto = MoshCrypto.fromPrintableKey(MoshVectors.SESSION_KEY)

    private val hostOutput = StringBuilder()
    private var resizes = ArrayList<Pair<Int, Int>>()
    private var echoAcks = ArrayList<Long>()

    private val listener = object : MoshTransport.Listener {
        override fun onHostBytes(bytes: ByteArray) {
            hostOutput.append(String(bytes, Charsets.ISO_8859_1))
        }

        override fun onHostResize(width: Int, height: Int) {
            resizes.add(width to height)
        }

        override fun onEchoAck(number: Long) {
            echoAcks.add(number)
        }
    }

    private val transport = MoshTransport(crypto, listener)

    private var serverSequence = 100L
    private var serverFragmentId = 1000L

    /** What we would have put on the wire, read back as the server would read it. */
    private fun ours(datagram: ByteArray): MoshInstruction {
        val opened = crypto.open(datagram)
        assertTrue("the client's packets are addressed to the server", !opened.nonce.toClient)
        val packet = MoshPacket.decode(opened.plaintext)
        val payload = MoshFragmentAssembly().add(MoshFragment.decode(packet.payload))
        return MoshInstruction.decode(checkNotNull(payload) { "a short instruction fits one fragment" })
    }

    private fun reassemble(datagrams: List<ByteArray>): MoshInstruction {
        val assembly = MoshFragmentAssembly()
        var payload: ByteArray? = null
        for (datagram in datagrams) {
            val packet = MoshPacket.decode(crypto.open(datagram).plaintext)
            payload = assembly.add(MoshFragment.decode(packet.payload)) ?: payload
        }
        return MoshInstruction.decode(checkNotNull(payload))
    }

    private fun fromServer(
        now: Long,
        oldNumber: Long = 0,
        newNumber: Long = 1,
        ackNumber: Long = 0,
        diff: ByteArray = ByteArray(0),
        reply: Int = MoshTiming.NONE,
        fragmentId: Long = serverFragmentId++,
    ): ByteArray {
        val instruction = MoshInstruction(
            oldNumber = oldNumber, newNumber = newNumber, ackNumber = ackNumber, diff = diff,
        )
        val fragment = MoshFragment(fragmentId, 0, final = true, contents = instruction.encode())
        val packet = MoshPacket(MoshTiming.stamp(now), reply, fragment.encode())
        return crypto.seal(MoshNonce(serverSequence++, toClient = true), packet.encode())
    }

    private fun hostBytes(text: String): ByteArray =
        ProtoWriter().bytes(
            1,
            ProtoWriter().bytes(2, ProtoWriter().bytes(4, text.toByteArray()).toByteArray()).toByteArray(),
        ).toByteArray()

    /**
     * Brings the session to where a real one is after its first exchange.
     *
     * It matters because mosh starts from a deliberately pessimistic round trip of a second, which
     * makes the frame rate four a second: until a real measurement arrives, typing waits up to
     * 250 ms to go out. The server's log says the same thing in its own words — `frame rate=4.00`
     * on the first packets. After one echoed timestamp the estimate is the truth and the frame
     * rate is mosh's 50 a second.
     */
    private fun handshake() {
        transport.tick(1_000)
        transport.onDatagram(fromServer(now = 1_050, reply = MoshTiming.stamp(1_030)), 1_050)
        assertEquals(20.0, transport.timing.srtt, 0.01)
    }

    // ───────────────────────────── opening and heartbeat ─────────────────────────────

    @Test fun `the first tick opens the conversation`() {
        val datagrams = transport.tick(1_000)
        assertEquals(1, datagrams.size)
        val instruction = ours(datagrams.single())
        assertEquals(MoshInstruction.PROTOCOL_VERSION, instruction.protocolVersion)
        assertEquals(0L, instruction.oldNumber)
        assertEquals("an empty state we have not sent before", 1L, instruction.newNumber)
        assertEquals(0, instruction.diff.size)
    }

    @Test fun `with nothing happening, one heartbeat every three seconds`() {
        transport.tick(1_000)
        assertTrue("silence until the heartbeat is due", transport.tick(2_000).isEmpty())
        assertTrue(transport.tick(3_999).isEmpty())
        assertEquals(1, transport.tick(4_000).size)
        assertTrue(transport.tick(5_000).isEmpty())
        assertEquals(1, transport.tick(7_000).size)
    }

    @Test fun `the wakeup time is when something is actually due`() {
        transport.tick(1_000)
        assertEquals(4_000L, transport.nextWakeup())
    }

    // ───────────────────────────── typing ─────────────────────────────

    @Test fun `a burst of keys leaves as one packet`() {
        handshake()
        transport.type("a".toByteArray())
        // Within the minimum delay nothing goes out: that is what makes a burst one packet.
        assertTrue(transport.tick(1_062).isEmpty())
        transport.type("b".toByteArray())
        transport.type("c".toByteArray())

        val datagrams = transport.tick(1_075)
        assertEquals(1, datagrams.size)
        val instruction = ours(datagrams.single())
        assertEquals("the keys are coalesced", listOf("abc"), keystrokesIn(instruction.diff))
    }

    @Test fun `a resize travels in order with the typing around it`() {
        handshake()
        transport.type("x".toByteArray())
        transport.resize(100, 40)
        transport.type("y".toByteArray())

        transport.tick(1_060)                       // notices it and schedules
        val instruction = ours(transport.tick(1_075).single())
        val extensions = ArrayList<Int>()
        val outer = ProtoReader(instruction.diff)
        outer.forEachField { _, _ ->
            val inner = ProtoReader(outer.readBytes())
            inner.forEachField { ext, wire ->
                extensions.add(ext)
                inner.skip(wire)
            }
        }
        assertEquals("keystroke, resize, keystroke", listOf(2, 3, 2), extensions)
    }

    @Test fun `a long paste is split across datagrams and arrives whole`() {
        handshake()
        // Deliberately incompressible: a repeating pattern of this length would come out of zlib
        // small enough to fit one datagram and the fragmentation would never be exercised.
        val paste = ByteArray(4_000)
        java.util.Random(7).nextBytes(paste)
        transport.type(paste)
        transport.tick(1_060)
        val datagrams = transport.tick(1_075)
        assertTrue("4000 incompressible bytes do not fit in one datagram", datagrams.size > 1)
        assertEquals(listOf(String(paste, Charsets.ISO_8859_1)), keystrokesIn(reassemble(datagrams).diff))
    }

    // ───────────────────────────── acknowledgement and retransmission ─────────────────────────────

    @Test fun `a state nobody acknowledges is offered again`() {
        handshake()
        transport.type("hello".toByteArray())
        transport.tick(1_060)
        val first = ours(transport.tick(1_075).single())
        assertTrue(transport.tick(1_100).isEmpty())

        // Past the round-trip timeout with no acknowledgement, the state stops being assumed
        // received and the same diff goes out again under the same number.
        val again = ours(transport.tick(1_300).single())
        assertEquals("the same state, offered again", first.newNumber, again.newNumber)
        assertEquals("carrying the same typing", keystrokesIn(first.diff), keystrokesIn(again.diff))
        // And computed from further back: by now we do not believe the earlier state arrived
        // either, so the diff is rebuilt from the oldest one the server can still be holding.
        assertTrue("rewound from ${first.oldNumber} to ${again.oldNumber}", again.oldNumber < first.oldNumber)
    }

    @Test fun `an acknowledged state is not offered again`() {
        handshake()
        transport.type("hello".toByteArray())
        transport.tick(1_060)
        val sent = ours(transport.tick(1_075).single())

        transport.onDatagram(fromServer(now = 1_100, oldNumber = 1, newNumber = 2, ackNumber = sent.newNumber), 1_100)
        // Only the heartbeat from here on: there is nothing outstanding.
        val later = transport.tick(1_300)
        assertTrue("nothing to retransmit", later.isEmpty() || ours(later.single()).diff.isEmpty())
    }

    @Test fun `what the host says reaches the listener and is acknowledged quickly`() {
        handshake()
        transport.onDatagram(fromServer(now = 1_100, oldNumber = 1, newNumber = 2, diff = hostBytes("hi there")), 1_100)
        assertEquals("hi there", hostOutput.toString())

        // Data owes an acknowledgement within a hundred milliseconds rather than at the next
        // heartbeat, and the tick that notices it is what schedules it: an I/O loop therefore has
        // to tick after every datagram and then wait until nextWakeup, which is what phase 6 does.
        assertTrue(transport.tick(1_150).isEmpty())
        assertEquals(1_250L, transport.nextWakeup())
        assertEquals(2L, ours(transport.tick(1_250).single()).ackNumber)
    }

    @Test fun `a resize and an echo ack from the host are passed on`() {
        handshake()
        val diff = ProtoWriter()
            .bytes(1, ProtoWriter().bytes(3, ProtoWriter().varint(5, 132).varint(6, 43).toByteArray()).toByteArray())
            .bytes(1, ProtoWriter().bytes(7, ProtoWriter().varint(8, 9).toByteArray()).toByteArray())
            .toByteArray()
        transport.onDatagram(fromServer(now = 1_100, oldNumber = 1, newNumber = 2, diff = diff), 1_100)
        assertEquals(listOf(132 to 43), resizes)
        assertEquals(listOf(9L), echoAcks)
    }

    @Test fun `the same frame twice is applied once`() {
        handshake()
        transport.onDatagram(fromServer(now = 1_100, oldNumber = 1, newNumber = 2, diff = hostBytes("once")), 1_100)
        // A retransmission: a new fragment id, the same instruction.
        transport.onDatagram(fromServer(now = 1_150, oldNumber = 1, newNumber = 2, diff = hostBytes("once")), 1_150)
        assertEquals("once", hostOutput.toString())
    }

    @Test fun `a diff against a state we do not hold is left alone`() {
        handshake()
        // We hold state 1. This one claims to start from 5, so applying it would corrupt the
        // screen; it must not be applied and must not be acknowledged either.
        transport.onDatagram(fromServer(now = 1_100, oldNumber = 5, newNumber = 6, diff = hostBytes("lost")), 1_100)
        assertEquals("", hostOutput.toString())
        transport.tick(1_150)
        assertEquals("still acknowledging the state we really hold", 1L, ours(transport.tick(1_250).single()).ackNumber)
    }

    @Test fun `a frame we cannot use still gets an answer at once`() {
        handshake()
        // The server is behind: it does not yet know which state we hold, so it is sending — and
        // resending, at frame rate — a diff we have to throw away. Waiting for the three-second
        // heartbeat to tell it so would stall the screen; the acknowledgement is due immediately.
        transport.onDatagram(fromServer(now = 1_100, oldNumber = 0, newNumber = 7, diff = hostBytes("lost")), 1_100)
        assertTrue(transport.tick(1_150).isEmpty())
        assertEquals("the ack is scheduled, not left to the heartbeat", 1_250L, transport.nextWakeup())
        assertEquals(1L, ours(transport.tick(1_250).single()).ackNumber)
    }

    @Test fun `our own datagram played back at us is refused`() {
        val mine = transport.tick(1_000).single()
        try {
            transport.onDatagram(mine, 1_100)
            fail("a to-server packet arriving at the client is our own")
        } catch (e: MoshProtocolException) {
            assertTrue(e.message!!.contains("played back"))
        }
    }

    @Test fun `a packet that fails its integrity check is refused`() {
        transport.tick(1_000)
        val bad = fromServer(now = 1_100).copyOf()
        bad[bad.size - 2] = (bad[bad.size - 2].toInt() xor 0xFF).toByte()
        try {
            transport.onDatagram(bad, 1_100)
            fail("a tampered datagram must not be accepted")
        } catch (e: MoshProtocolException) {
            assertTrue(e.message!!.contains("integrity"))
        }
    }

    // ───────────────────────────── timing and roaming ─────────────────────────────

    @Test fun `the round trip is measured from the echoed timestamp`() {
        transport.tick(1_000)
        assertEquals("mosh's pessimistic first guess", 1000.0, transport.timing.srtt, 0.01)
        // The server echoes a timestamp of ours from 40 ms ago.
        transport.onDatagram(fromServer(now = 2_000, reply = MoshTiming.stamp(1_960)), 2_000)
        assertEquals("the first measurement replaces the guess outright", 40.0, transport.timing.srtt, 0.01)
        assertEquals("and the frame rate follows it", MoshTiming.SEND_INTERVAL_MIN, transport.timing.sendInterval())
    }

    @Test fun `the local port is hopped only when nothing is getting through`() {
        handshake()
        transport.type("a".toByteArray())
        transport.tick(1_060)
        val sent = ours(transport.tick(1_075).single())
        transport.onDatagram(fromServer(now = 1_100, oldNumber = 1, newNumber = 2, ackNumber = sent.newNumber), 1_100)
        assertTrue("something round-tripped just now", !transport.shouldHopLocalPort(1_200, lastHopAt = 0))

        // Ten seconds of silence is mosh's threshold for suspecting the local binding.
        assertTrue(transport.shouldHopLocalPort(12_000, lastHopAt = 0))
        assertTrue("and not twice in a row", !transport.shouldHopLocalPort(12_000, lastHopAt = 11_000))
    }

    // ───────────────────────────── closing ─────────────────────────────

    @Test fun `the closing handshake claims the shutdown state until it is acknowledged`() {
        handshake()
        transport.startShutdown(1_100)
        val instruction = ours(transport.tick(1_200).single())
        assertEquals("all ones, as an unsigned 64-bit number", MoshTransport.SHUTDOWN_NUMBER, instruction.newNumber)
        assertTrue(!transport.shutdownAcknowledged)

        transport.onDatagram(
            fromServer(now = 1_300, oldNumber = 1, newNumber = 2, ackNumber = MoshTransport.SHUTDOWN_NUMBER), 1_300,
        )
        assertTrue("the server has seen us go", transport.shutdownAcknowledged)
    }

    @Test fun `a closing handshake nobody answers gives up`() {
        handshake()
        transport.startShutdown(1_100)
        assertTrue(!transport.shutdownTimedOut(1_200))
        assertTrue("after ten seconds there is nobody there", transport.shutdownTimedOut(11_200))
    }

    @Test fun `a server that goes away is acknowledged and reported`() {
        handshake()
        transport.onDatagram(fromServer(now = 1_100, oldNumber = 1, newNumber = MoshTransport.SHUTDOWN_NUMBER), 1_100)
        assertTrue(transport.counterpartyShutdown)
        assertEquals(MoshTransport.SHUTDOWN_NUMBER, ours(transport.tick(1_200).single()).ackNumber)
    }

    // ───────────────────────────── chaff ─────────────────────────────

    @Test fun `two identical packets do not look identical on the wire`() {
        // The padding is random, so the length of a datagram says nothing about what was typed.
        handshake()
        transport.type("a".toByteArray())
        transport.tick(1_060)
        val first = transport.tick(1_075).single()
        val sizes = HashSet<Int>()
        repeat(40) {
            val t = MoshTransport(crypto, listener)
            t.tick(1_000)
            t.type("a".toByteArray())
            t.tick(1_200)
            sizes.add(t.tick(1_300).single().size)
        }
        assertTrue("the same keystroke does not always make the same size: $sizes", sizes.size > 1)
        assertNotEquals(0, first.size)
    }

    private fun keystrokesIn(diff: ByteArray): List<String> {
        val out = ArrayList<String>()
        val outer = ProtoReader(diff)
        outer.forEachField { _, _ ->
            val inner = ProtoReader(outer.readBytes())
            inner.forEachField { ext, wire ->
                if (ext == 2) {
                    val keystroke = ProtoReader(inner.readBytes())
                    keystroke.forEachField { field, w ->
                        if (field == 4) out.add(String(keystroke.readBytes(), Charsets.ISO_8859_1))
                        else keystroke.skip(w)
                    }
                } else {
                    inner.skip(wire)
                }
            }
        }
        return out
    }
}
