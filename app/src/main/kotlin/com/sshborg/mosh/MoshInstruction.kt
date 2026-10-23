package com.sshborg.mosh

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * The transport instruction: the envelope that carries one state diff and the bookkeeping that
 * makes the exchange idempotent (`docs/mosh-wire-format.md` §2).
 *
 * [oldNumber] and [newNumber] say which state the diff goes *from* and *to*, which is what lets
 * either side skip intermediate states entirely — the reason a session that was asleep for an hour
 * repaints at once instead of replaying the hour. [ackNumber] reports the newest state of the
 * other side's we hold; [throwawayNumber] says which of ours it may forget.
 *
 * The serialised message is zlib-compressed before it is fragmented, with the ordinary zlib
 * header — `Deflater`/`Inflater` at their defaults, *not* `nowrap`.
 */
class MoshInstruction(
    val protocolVersion: Int = PROTOCOL_VERSION,
    val oldNumber: Long = 0,
    val newNumber: Long = 0,
    val ackNumber: Long = 0,
    val throwawayNumber: Long = 0,
    val diff: ByteArray = EMPTY,
    val chaff: ByteArray = EMPTY,
) {

    fun encode(): ByteArray = deflate(serialize())

    fun serialize(): ByteArray {
        val w = ProtoWriter()
            .varint(FIELD_VERSION, protocolVersion.toLong())
            .varint(FIELD_OLD_NUM, oldNumber)
            .varint(FIELD_NEW_NUM, newNumber)
            .varint(FIELD_ACK_NUM, ackNumber)
            .varint(FIELD_THROWAWAY_NUM, throwawayNumber)
        if (diff.isNotEmpty()) w.bytes(FIELD_DIFF, diff)
        // Padding of a random length, which mosh sends to keep the size of a datagram from
        // reporting how much was typed. We send it for the same reason and ignore it on the way in.
        if (chaff.isNotEmpty()) w.bytes(FIELD_CHAFF, chaff)
        return w.toByteArray()
    }

    override fun toString(): String =
        "MoshInstruction(v=$protocolVersion, $oldNumber=>$newNumber, ack=$ackNumber, " +
            "throwaway=$throwawayNumber, diff=${diff.size}B)"

    companion object {
        const val PROTOCOL_VERSION = 2

        private const val FIELD_VERSION = 1
        private const val FIELD_OLD_NUM = 2
        private const val FIELD_NEW_NUM = 3
        private const val FIELD_ACK_NUM = 4
        private const val FIELD_THROWAWAY_NUM = 5
        private const val FIELD_DIFF = 6
        private const val FIELD_CHAFF = 7

        /** Mosh's own ceiling on a decompressed instruction, and our guard against a bomb. */
        private const val MAX_DECOMPRESSED = 2048 * 2048

        private val EMPTY = ByteArray(0)

        fun decode(fragmentPayload: ByteArray): MoshInstruction = parse(inflate(fragmentPayload))

        fun parse(serialized: ByteArray): MoshInstruction {
            var version = 0
            var oldNum = 0L
            var newNum = 0L
            var ack = 0L
            var throwaway = 0L
            var diff = EMPTY
            val r = ProtoReader(serialized)
            r.forEachField { field, wireType ->
                when {
                    field == FIELD_VERSION && wireType == WIRE_VARINT -> version = r.readVarint().toInt()
                    field == FIELD_OLD_NUM && wireType == WIRE_VARINT -> oldNum = r.readVarint()
                    field == FIELD_NEW_NUM && wireType == WIRE_VARINT -> newNum = r.readVarint()
                    field == FIELD_ACK_NUM && wireType == WIRE_VARINT -> ack = r.readVarint()
                    field == FIELD_THROWAWAY_NUM && wireType == WIRE_VARINT -> throwaway = r.readVarint()
                    field == FIELD_DIFF && wireType == WIRE_BYTES -> diff = r.readBytes()
                    field == FIELD_CHAFF && wireType == WIRE_BYTES -> r.readBytes()  // padding
                    else -> r.skip(wireType)
                }
            }
            if (version != PROTOCOL_VERSION) {
                // Mosh treats this as fatal rather than as something to negotiate, and so must we:
                // the state numbering is the protocol, and guessing at a different one corrupts
                // the screen instead of failing cleanly.
                throw MoshProtocolException("mosh protocol version $version, expected $PROTOCOL_VERSION")
            }
            return MoshInstruction(version, oldNum, newNum, ack, throwaway, diff)
        }

        internal fun deflate(raw: ByteArray): ByteArray {
            val deflater = Deflater()
            try {
                deflater.setInput(raw)
                deflater.finish()
                val out = ByteArrayOutputStream(raw.size + 64)
                val buffer = ByteArray(4096)
                while (!deflater.finished()) {
                    out.write(buffer, 0, deflater.deflate(buffer))
                }
                return out.toByteArray()
            } finally {
                deflater.end()
            }
        }

        internal fun inflate(compressed: ByteArray): ByteArray {
            val inflater = Inflater()
            try {
                inflater.setInput(compressed)
                val out = ByteArrayOutputStream(compressed.size * 4)
                val buffer = ByteArray(4096)
                while (!inflater.finished()) {
                    val n = inflater.inflate(buffer)
                    if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                        throw MoshProtocolException("truncated compressed instruction")
                    }
                    out.write(buffer, 0, n)
                    if (out.size() > MAX_DECOMPRESSED) {
                        throw MoshProtocolException("instruction decompresses beyond $MAX_DECOMPRESSED bytes")
                    }
                }
                return out.toByteArray()
            } catch (e: java.util.zip.DataFormatException) {
                throw MoshProtocolException("instruction is not zlib data", e)
            } finally {
                inflater.end()
            }
        }
    }
}
