package com.sshborg.mosh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        val opened = checkNotNull(crypto.open(MoshVectors.serverDatagram)) { "mosh's own datagram" }
        assertArrayEquals(MoshVectors.serverPlaintext, opened.plaintext)
        assertTrue("the server's packets are addressed to the client", opened.nonce.toClient)
        assertEquals(0L, opened.nonce.sequence)
    }

    @Test fun `re-sealing mosh's plaintext reproduces mosh's own datagram`() {
        val opened = checkNotNull(crypto.open(MoshVectors.serverDatagram)) { "mosh's own datagram" }
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
            assertArrayEquals(plain, checkNotNull(c.open(c.seal(nonce, plain))).plaintext)
        }
    }

    @Test fun `a tampered datagram comes back as nothing, not as an error`() {
        val bad = MoshVectors.serverDatagram.copyOf()
        bad[bad.size - 1] = (bad[bad.size - 1].toInt() xor 0x01).toByte()
        assertNull("a datagram with a broken tag must not open", crypto.open(bad))
    }

    @Test fun `a datagram too short to hold a nonce and a tag comes back as nothing`() {
        assertNull("23 bytes cannot be a mosh datagram", crypto.open(ByteArray(23)))
    }

    /**
     * The line between the two answers, which is the point of returning null at all: a packet
     * anyone could have sent is dropped without a word, and a packet that got past the tag — so
     * came from whoever holds the key — keeps its reason even when it is nonsense inside.
     */
    @Test fun `what authenticated and is still wrong keeps its reason`() {
        val nonsense = crypto.seal(MoshNonce(sequence = 7, toClient = true), byteArrayOf(0x7F))
        assertNotNull("it was sealed with the session key, so it must open", crypto.open(nonsense))
        try {
            MoshPacket.decode(crypto.open(nonsense)!!.plaintext)
            fail("one stray byte is not a mosh packet")
        } catch (e: MoshProtocolException) {
            assertTrue("the reason is what this case is for: ${e.message}", e.message!!.isNotEmpty())
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

    /**
     * Five hundred callers at once must come away with five hundred different numbers.
     *
     * It would pass today without being asked: the counter is an `AtomicLong`, and it is touched
     * from one thread anyway because keystrokes reach the protocol loop through a queue. The test
     * is here for the day that stops being true. Two callers taking the same number is the one
     * irreversible fault in this protocol — a repeated nonce under OCB does not weaken the
     * session, it makes it forgeable — so the invariant is worth pinning before a refactor finds
     * out the hard way. Taken from the iOS client, which arrived at it independently.
     */
    @Test fun `five hundred callers at once get five hundred different numbers`() {
        val seq = MoshSequence()
        val callers = 500
        val start = java.util.concurrent.CountDownLatch(1)
        val done = java.util.concurrent.CountDownLatch(callers)
        val taken = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
        val pool = java.util.concurrent.Executors.newFixedThreadPool(32)
        try {
            repeat(callers) {
                pool.execute {
                    start.await()
                    taken.add(seq.next())
                    done.countDown()
                }
            }
            start.countDown()                        // let them all go at the same moment
            assertTrue("the callers did not finish", done.await(30, java.util.concurrent.TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }
        assertEquals("a number was handed out twice", callers, taken.size)
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
