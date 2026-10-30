package com.sshborg.mosh

import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.OCBBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong

/**
 * The outermost layer of a mosh datagram: `nonce[8] || OCB-AES-128(plaintext)`, where the
 * 16-byte tag is part of the ciphertext and there is no associated data. See
 * `docs/mosh-wire-format.md` §1; the format is interoperable with mosh 1.4.0, verified against a
 * live `mosh-server` rather than inferred.
 *
 * The cipher is OCB **version 3**, which is RFC 7253, so BouncyCastle's [OCBBlockCipher] is the
 * same algorithm mosh carries — that is why this needs no native code and no new dependency.
 *
 * Nothing here touches Android: the whole protocol is plain JVM so the unit-test suite can drive
 * it, and so the lab harness can run the same classes the app runs.
 */
class MoshCrypto(key: ByteArray) {

    private val keyParam: KeyParameter

    /**
     * OCB's security argument degrades with the number of blocks encrypted under one key, and
     * mosh kills the session at 2^47 rather than let it drift into the unsafe region. Both ends
     * share the key, so the budget is shared too.
     */
    private var blocksEncrypted = 0L

    init {
        require(key.size == KEY_BYTES) { "a mosh key is $KEY_BYTES bytes, got ${key.size}" }
        keyParam = KeyParameter(key)
    }

    /** What [open] recovered: the nonce as it travelled, and the plaintext under it. */
    class Opened(val nonce: MoshNonce, val plaintext: ByteArray)

    fun seal(nonce: MoshNonce, plaintext: ByteArray): ByteArray {
        val cipher = cipher(encrypting = true, nonce = nonce)
        val out = ByteArray(cipher.getOutputSize(plaintext.size))
        var n = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
        n += cipher.doFinal(out, n)

        blocksEncrypted += (plaintext.size + 15) / 16
        if (blocksEncrypted shr 47 != 0L) throw MoshProtocolException("encrypted 2^47 blocks under one key")

        val datagram = ByteArray(NONCE_WIRE_BYTES + n)
        nonce.writeWireTo(datagram, 0)
        System.arraycopy(out, 0, datagram, NONCE_WIRE_BYTES, n)
        return datagram
    }

    /**
     * @return the nonce and plaintext, or **null** when this datagram is not ours: too short to
     *   hold a nonce and a tag, or failing its integrity check.
     *
     * Null rather than an exception, and the line is drawn deliberately: *anyone* can send a
     * datagram to an open UDP port, so a packet that does not authenticate is the weather and not
     * an error. The caller drops it and counts it — there is nothing a reason would add, as our
     * own `catch (_: MoshProtocolException)` used to admit by never looking at it. On the JVM the
     * cost of throwing is filling in a stack trace, which an unauthenticated stranger could
     * otherwise make us do at will by spraying short packets at the port, on the one path that has
     * to stay cheap.
     *
     * Everything past this point still throws, because everything past this point required our
     * shared key: an instruction we cannot parse came from the real peer, which is a version
     * disagreement or a bug of ours, and there the reason is the whole value. That is the rule —
     * **null for what anyone can send us, an exception for what needed our key.**
     */
    fun open(datagram: ByteArray): Opened? {
        // Eight bytes of nonce and sixteen of tag have to be there before there can be a message.
        if (datagram.size < NONCE_WIRE_BYTES + TAG_BYTES) return null
        val nonce = MoshNonce.fromWire(datagram, 0)
        val cipher = cipher(encrypting = false, nonce = nonce)
        val body = datagram.size - NONCE_WIRE_BYTES
        val out = ByteArray(cipher.getOutputSize(body))
        return try {
            var n = cipher.processBytes(datagram, NONCE_WIRE_BYTES, body, out, 0)
            n += cipher.doFinal(out, n)
            Opened(nonce, out.copyOf(n))
        } catch (_: Exception) {
            // A failed tag is the ordinary case for a stray or tampered packet, not a bug.
            null
        }
    }

    private fun cipher(encrypting: Boolean, nonce: MoshNonce): OCBBlockCipher {
        val cipher = OCBBlockCipher(AESEngine.newInstance(), AESEngine.newInstance())
        cipher.init(encrypting, AEADParameters(keyParam, TAG_BYTES * 8, nonce.full()))
        return cipher
    }

    companion object {
        const val KEY_BYTES = 16
        const val NONCE_WIRE_BYTES = 8
        const val TAG_BYTES = 16

        /**
         * The key as `mosh-server` prints it on its `MOSH CONNECT` line: 22 base64 characters
         * with the padding left off.
         */
        fun fromPrintableKey(printable: String): MoshCrypto {
            val padded = printable.trim().let { it + "=".repeat((4 - it.length % 4) % 4) }
            val key = try {
                Base64.getDecoder().decode(padded)
            } catch (e: IllegalArgumentException) {
                throw MoshProtocolException("the key is not base64", e)
            }
            return MoshCrypto(key)
        }
    }
}

/**
 * The 64-bit value in front of every datagram: the top bit says which way the packet is going,
 * the rest is a sequence number. Twelve bytes reach the cipher (four zero bytes and these eight),
 * only the eight travel.
 */
class MoshNonce(val sequence: Long, val toClient: Boolean) {

    init {
        require(sequence >= 0) { "a sequence number is never negative" }
    }

    fun value(): Long = (if (toClient) DIRECTION_TO_CLIENT else 0L) or (sequence and SEQUENCE_MASK)

    fun full(): ByteArray = ByteArray(12).also { writeWireTo(it, 4) }

    fun writeWireTo(dst: ByteArray, offset: Int) {
        val v = value()
        for (i in 0 until 8) dst[offset + i] = (v ushr (56 - 8 * i)).toByte()
    }

    override fun toString(): String = "MoshNonce(seq=$sequence, ${if (toClient) "to-client" else "to-server"})"

    companion object {
        const val DIRECTION_TO_CLIENT = 1L shl 63
        const val SEQUENCE_MASK = DIRECTION_TO_CLIENT.inv()

        fun fromWire(src: ByteArray, offset: Int): MoshNonce {
            var v = 0L
            for (i in 0 until 8) v = (v shl 8) or (src[offset + i].toLong() and 0xFF)
            return MoshNonce(v and SEQUENCE_MASK, (v and DIRECTION_TO_CLIENT) != 0L)
        }
    }
}

/**
 * The sequence numbers we put on the wire.
 *
 * OCB with a repeated nonce is not merely weakened, it is forgeable, so this counter exists to
 * make repetition impossible rather than merely unlikely: it only ever goes up, it is atomic, and
 * it refuses to wrap. It must outlive the socket — a new local port after a network change is
 * still the same key, so the count carries on from where it was.
 *
 * **And it must never be restarted under a key that has already been used.** Today that is free:
 * a session lives in memory in [com.sshborg.service.SessionManager], the key goes from the
 * bootstrap line into [MoshCrypto] and nowhere else, and if the process dies the session dies with
 * it. But the foreground service exists precisely to keep sessions alive, so "restore the sessions
 * after the process was killed" is a feature somebody will reasonably propose — and on that day a
 * counter that starts again at 1 under the same key is a forgeable session, silently. If a session
 * is ever restored, this number is restored with it or the key is not reused. The iOS client
 * reached the same conclusion from the other end, where the system suspends the process for you.
 */
class MoshSequence(start: Long = 1L) {

    private val next = AtomicLong(start)

    fun next(): Long {
        val v = next.getAndIncrement()
        if (v < 0 || v >= MoshNonce.SEQUENCE_MASK) throw MoshProtocolException("sequence number exhausted")
        return v
    }
}

/**
 * The receiving half of the same rule, as mosh has it (`network.cc:513-519`), and it is
 * security-sensitive in two ways that are easy to miss.
 *
 * A packet carrying our *own* direction bit is our own traffic played back at us and is refused
 * outright. A packet whose sequence number is not ahead of what we have already seen is still
 * handed on — the state layer is idempotent and will do the right thing with a duplicate — but it
 * must not be allowed to move the peer address or the clock, because that is what a replay would
 * otherwise achieve. In the lab this is the first thing to get wrong: a second probe run that
 * restarts its counter is authenticated and parsed, and then answered to the previous run's port.
 */
class MoshReplayFilter(private val weAreClient: Boolean = true) {

    private var expected = 0L

    /** @return true if this packet may also update timing and the peer's address. */
    fun accept(nonce: MoshNonce): Boolean {
        if (nonce.toClient != weAreClient) {
            throw MoshProtocolException("packet addressed the wrong way: our own traffic played back")
        }
        if (nonce.sequence < expected) return false
        expected = nonce.sequence + 1
        return true
    }
}

/** Anything malformed, unauthenticated or out of budget in the protocol layers. */
class MoshProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)
