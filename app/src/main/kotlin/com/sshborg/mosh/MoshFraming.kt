package com.sshborg.mosh

/**
 * What sits inside the encryption: two timestamps and one fragment of an instruction.
 *
 * ```
 * plaintext:  timestamp[2] || timestamp_reply[2] || fragment
 * fragment:   id[8] || (final<<15 | fragment_num)[2] || payload
 * ```
 *
 * Everything is big-endian. See `docs/mosh-wire-format.md` §1.
 */
class MoshPacket(val timestamp: Int, val timestampReply: Int, val payload: ByteArray) {

    fun encode(): ByteArray {
        val out = ByteArray(4 + payload.size)
        writeBE16(out, 0, timestamp)
        writeBE16(out, 2, timestampReply)
        System.arraycopy(payload, 0, out, 4, payload.size)
        return out
    }

    companion object {
        /** The value both timestamp fields use for "nothing to say", i.e. 16 bits of ones. */
        const val NONE = 0xFFFF

        fun decode(plaintext: ByteArray): MoshPacket {
            if (plaintext.size < 4) throw MoshProtocolException("packet without its timestamps")
            return MoshPacket(
                timestamp = readBE16(plaintext, 0),
                timestampReply = readBE16(plaintext, 2),
                payload = plaintext.copyOfRange(4, plaintext.size),
            )
        }
    }
}

/**
 * One piece of an instruction. The instruction id ties the pieces together, and the top bit of
 * the second field marks the last one — so the receiver knows the total only when it arrives,
 * which is why assembly counts rather than preallocates.
 */
class MoshFragment(val id: Long, val number: Int, val final: Boolean, val contents: ByteArray) {

    init {
        require(number in 0..0x7FFF) { "fragment number $number does not fit in 15 bits" }
    }

    fun encode(): ByteArray {
        val out = ByteArray(HEADER_BYTES + contents.size)
        writeBE64(out, 0, id)
        writeBE16(out, 8, (if (final) 0x8000 else 0) or number)
        System.arraycopy(contents, 0, out, HEADER_BYTES, contents.size)
        return out
    }

    companion object {
        const val HEADER_BYTES = 10

        fun decode(bytes: ByteArray): MoshFragment {
            if (bytes.size < HEADER_BYTES) throw MoshProtocolException("fragment without its header")
            val combined = readBE16(bytes, 8)
            return MoshFragment(
                id = readBE64(bytes, 0),
                number = combined and 0x7FFF,
                final = (combined and 0x8000) != 0,
                contents = bytes.copyOfRange(HEADER_BYTES, bytes.size),
            )
        }
    }
}

/**
 * Collects the fragments of one instruction.
 *
 * A fragment of a different id abandons whatever was being assembled: mosh does the same, because
 * the sender has moved on and an instruction it no longer sends is of no use to us. A
 * retransmission of a fragment we already hold is dropped rather than counted twice — the sender
 * reuses the id when it resends an unchanged instruction, which is what makes that case normal
 * rather than suspicious.
 *
 * One deliberate difference from mosh: an assembly is handed over **once**. Mosh returns the
 * completed instruction again for every repeat of the final fragment, and relies on the state
 * layer's idempotency to absorb it; ours is idempotent too, but re-delivering means decompressing
 * and parsing the whole instruction again on each retransmission, which for a full-screen repaint
 * is work a peer could ask for at will.
 */
class MoshFragmentAssembly {

    private var currentId = -1L
    private var arrived = 0
    private var total = -1
    private var delivered = false
    private var refused = false
    private var fragments = arrayOfNulls<MoshFragment>(0)

    /**
     * How much payload is being held for an instruction nobody has finished sending. Public
     * because it is the thing a limit on memory has to be stated about: an assertion that the
     * ceiling was *reported* passes while it goes on being exceeded.
     */
    var bytesHeld = 0
        private set

    /** How many fragments the largest instruction we have assembled was split into. */
    var largestAssembly = 0
        private set

    /**
     * How many times a fragment arrived with an id and number we already held but **different
     * contents**. It should never happen, and nothing here can repair it if it does: it would mean
     * the sender reuses an id for a changed instruction, which is exactly what the "deliver once"
     * rule above relies on it not doing. Counting it is how that reliance gets checked against a
     * real server instead of trusted.
     */
    var conflictingFragments = 0
        private set

    /** @return the complete payload once the last missing piece arrives, otherwise null. */
    fun add(fragment: MoshFragment): ByteArray? {
        if (fragment.id != currentId) {
            currentId = fragment.id
            arrived = 0
            total = -1
            delivered = false
            refused = false
            fragments = arrayOfNulls(INITIAL_SLOTS)
            bytesHeld = 0
        }
        // Every refusal below is about the instruction, not about the piece that happened to
        // arrive when it was noticed: all three are impossible from a sender that is working, so
        // once one is raised there is nothing left to assemble under this id and no reason to keep
        // holding what arrived before it. An exception does not undo what the object already did
        // to itself — the caller counts it, drops the datagram and goes on reading, which is the
        // right response to one unusable datagram and no help at all against a peer that keeps
        // sending them. So the undoing is done here.
        if (refused) throw MoshProtocolException("id ${fragment.id} was refused and is not being assembled")
        // The last fragment is what says how many there are, so it is also the only thing that
        // can contradict itself. Two different answers for one id means the pieces on the floor
        // belong to two instructions, and no amount of waiting will make them into one.
        if (fragment.final) {
            val announced = fragment.number + 1
            if (total >= 0 && total != announced) {
                refuse("id ${fragment.id} has two different last fragments")
            }
            total = announced
            // And a piece already held above it, which is the same contradiction arriving in the
            // other order: it was taken in while the total was unknown.
            for (i in announced until fragments.size) {
                if (fragments[i] != null) {
                    refuse("id ${fragment.id} holds a fragment past its last one")
                }
            }
        }
        // A piece numbered past the last one. Harmless to drop, and not harmless to keep: it
        // would be counted among the pieces that have arrived while the hole it does not fill
        // stays open, which is how a count that adds up came to sit on top of a missing
        // fragment 0 — and the assembly then dereferenced it.
        if (total >= 0 && fragment.number >= total) {
            refuse("fragment ${fragment.number} of id ${fragment.id} is past its last one")
        }
        val held = fragments.getOrNull(fragment.number)
        if (held == null) {
            // The ceiling on what may be held for an instruction nobody has finished sending. The
            // 4 MiB ceiling on an instruction itself applies to the decompressed bytes and
            // therefore only after assembly; with fifteen bits of fragment numbers to fill, there
            // is room for many times that much to sit here waiting for a last fragment that never
            // comes. Asked *before* the piece is taken in, so the number is a ceiling and not a
            // line the object reports crossing on its way past it — and before the array is grown
            // too, so a refusal costs nothing at all.
            if (bytesHeld + fragment.contents.size > MAX_HELD_BYTES) {
                refuse("id ${fragment.id} would hold more than $MAX_HELD_BYTES bytes unassembled")
            }
            if (fragment.number >= fragments.size) {
                fragments = fragments.copyOf(maxOf(fragment.number + 1, fragments.size * 2))
            }
            fragments[fragment.number] = fragment
            arrived++
            bytesHeld += fragment.contents.size
        } else if (!held.contents.contentEquals(fragment.contents)) {
            conflictingFragments++
        }
        if (total < 0 || arrived < total || delivered) return null
        // A count of pieces is not the same as having the right ones. Every rule above exists to
        // keep those two in step; this is the one that does not have to be believed.
        if ((0 until total).any { fragments[it] == null }) return null
        delivered = true
        if (total > largestAssembly) largestAssembly = total

        val size = (0 until total).sumOf { fragments[it]!!.contents.size }
        val out = ByteArray(size)
        var at = 0
        for (i in 0 until total) {
            val piece = fragments[i]!!.contents
            System.arraycopy(piece, 0, out, at, piece.size)
            at += piece.size
        }
        return out
    }

    /**
     * Give up on this id: let go of the pieces, and refuse the rest of them until the sender moves
     * on to another instruction. The refusal is per instruction and not per session, because one
     * instruction we cannot assemble is no reason to lose a terminal.
     */
    private fun refuse(why: String): Nothing {
        refused = true
        fragments = arrayOfNulls(0)
        bytesHeld = 0
        arrived = 0
        total = -1
        throw MoshProtocolException(why)
    }

    companion object {
        private const val INITIAL_SLOTS = 8

        /**
         * How much unassembled payload one instruction may have waiting. An instruction is a
         * screen's worth of differences, so this is already far above anything a real one needs,
         * and the point is only that the number exists.
         */
        const val MAX_HELD_BYTES = 4 * 1024 * 1024
    }
}

/**
 * Splits an instruction's compressed payload into fragments.
 *
 * The id advances whenever the bytes to send differ from the last ones, and only then. That is
 * what lets the far end tell a retransmission from a new instruction — and it has to be the whole
 * payload rather than the state numbers, because the random chaff makes two otherwise identical
 * instructions different on the wire. Reusing an id for different bytes would be the one real
 * hazard here: the far end would mix the fragments of two instructions into one assembly.
 *
 * [budget] is what is left for the fragment *including* its ten-byte header, which is how mosh
 * expresses it — the caller has already taken the nonce, the timestamps and the tag out of the
 * path MTU.
 */
class MoshFragmenter {

    private var nextId = 0L
    private var lastPayload: ByteArray? = null
    private var lastBudget = -1

    fun fragments(instruction: MoshInstruction, budget: Int): List<MoshFragment> {
        val room = budget - MoshFragment.HEADER_BYTES
        require(room > 0) { "no room left for a fragment in $budget bytes" }

        val payload = instruction.encode()
        if (!payload.contentEquals(lastPayload) || room != lastBudget) nextId++
        lastPayload = payload
        lastBudget = room
        val out = ArrayList<MoshFragment>()
        var offset = 0
        var number = 0
        do {
            val end = minOf(offset + room, payload.size)
            val last = end == payload.size
            out.add(MoshFragment(nextId, number++, last, payload.copyOfRange(offset, end)))
            offset = end
        } while (!out.last().final)
        return out
    }
}

internal fun writeBE16(dst: ByteArray, offset: Int, value: Int) {
    dst[offset] = (value ushr 8).toByte()
    dst[offset + 1] = value.toByte()
}

internal fun writeBE64(dst: ByteArray, offset: Int, value: Long) {
    for (i in 0 until 8) dst[offset + i] = (value ushr (56 - 8 * i)).toByte()
}

internal fun readBE16(src: ByteArray, offset: Int): Int =
    ((src[offset].toInt() and 0xFF) shl 8) or (src[offset + 1].toInt() and 0xFF)

internal fun readBE64(src: ByteArray, offset: Int): Long {
    var v = 0L
    for (i in 0 until 8) v = (v shl 8) or (src[offset + i].toLong() and 0xFF)
    return v
}
