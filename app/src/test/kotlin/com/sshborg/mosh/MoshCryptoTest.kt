package com.sshborg.mosh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The datagram layer against mosh's own bytes.
 *
 * The decisive test is [`re-sealing mosh's plaintext reproduces mosh's own datagram`]: OCB is
 * deterministic given key, nonce and plaintext, so if our ciphertext matches the one mosh
 * produced, byte for byte, then the cipher, the key schedule, the nonce layout and the tag length
 * all agree with it. That is the interoperability claim the whole plan rests on, and it is checked
 * here rather than asserted in a document.
 */
class MoshCryptoTest {

    private val crypto get() = MoshCrypto.fromPrintableKey(MoshVectors.SESSION_KEY)

    @Test fun `a real datagram from mosh decrypts`() {
        val opened = crypto.open(MoshVectors.serverDatagram)
        assertArrayEquals(MoshVectors.serverPlaintext, opened.plaintext)
        assertTrue("the server's packets are addressed to the client", opened.nonce.toClient)
        assertEquals(0L, opened.nonce.sequence)
    }

    @Test fun `re-sealing mosh's plaintext reproduces mosh's own datagram`() {
        val opened = crypto.open(MoshVectors.serverDatagram)
        assertArrayEquals(MoshVectors.serverDatagram, crypto.seal(opened.nonce, opened.plaintext))
    }

    @Test fun `the nonce is the direction bit and a sequence number`() {
        val toServer = MoshNonce(sequence = 42, toClient = false)
        assertEquals(42L, toServer.value())
        assertEquals(12, toServer.full().size)
        // The four leading bytes of the twelve are zero; only the other eight travel.
        assertArrayEquals(ByteArray(4), toServer.full().copyOfRange(0, 4))

        val toClient = MoshNonce(sequence = 42, toClient = true)
        assertEquals(MoshNonce.DIRECTION_TO_CLIENT or 42L, toClient.value())

        val wire = ByteArray(8).also { toClient.writeWireTo(it, 0) }
        val back = MoshNonce.fromWire(wire, 0)
        assertEquals(42L, back.sequence)
        assertTrue(back.toClient)
    }

    @Test fun `our own round trip holds for an empty and a long payload`() {
        val c = crypto
        for (size in intArrayOf(0, 1, 15, 16, 17, 1000)) {
            val plain = ByteArray(size) { (it * 7).toByte() }
            val nonce = MoshNonce(sequence = size.toLong() + 1, toClient = false)
            assertArrayEquals(plain, c.open(c.seal(nonce, plain)).plaintext)
        }
    }

    @Test fun `a tampered datagram is refused rather than returned`() {
        val bad = MoshVectors.serverDatagram.copyOf()
        bad[bad.size - 1] = (bad[bad.size - 1].toInt() xor 0x01).toByte()
        try {
            crypto.open(bad)
            fail("a datagram with a broken tag must not open")
        } catch (e: MoshProtocolException) {
            assertTrue(e.message!!.contains("integrity"))
        }
    }

    @Test fun `a datagram too short to hold a nonce and a tag is refused`() {
        try {
            crypto.open(ByteArray(23))
            fail("23 bytes cannot be a mosh datagram")
        } catch (e: MoshProtocolException) {
            assertTrue(e.message!!.contains("nonce and tag"))
        }
    }

    @Test fun `a key of the wrong length is refused`() {
        try {
            MoshCrypto(ByteArray(15))
            fail("15 bytes is not a mosh key")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("16 bytes"))
        }
    }

    @Test fun `the sequence counter only ever goes up`() {
        val seq = MoshSequence()
        val first = seq.next()
        val second = seq.next()
        val third = seq.next()
        assertTrue("$first < $second < $third", first < second && second < third)
    }

    @Test fun `our own traffic played back at us is refused`() {
        val filter = MoshReplayFilter(weAreClient = true)
        try {
            filter.accept(MoshNonce(sequence = 5, toClient = false))
            fail("a to-server packet arriving at the client is our own, played back")
        } catch (e: MoshProtocolException) {
            assertTrue(e.message!!.contains("played back"))
        }
    }

    @Test fun `a sequence number that is not ahead is parsed but not trusted`() {
        val filter = MoshReplayFilter(weAreClient = true)
        assertTrue(filter.accept(MoshNonce(10, toClient = true)))
        // A replay of the same packet, or an older one arriving late: the state layer may still
        // see it, but it must not move the clock or the peer's address.
        assertFalse(filter.accept(MoshNonce(10, toClient = true)))
        assertFalse(filter.accept(MoshNonce(3, toClient = true)))
        assertTrue(filter.accept(MoshNonce(11, toClient = true)))
    }
}
