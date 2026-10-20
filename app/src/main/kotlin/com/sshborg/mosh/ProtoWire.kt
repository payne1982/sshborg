package com.sshborg.mosh

import java.io.ByteArrayOutputStream

/**
 * Just enough protocol-buffer wire format for mosh's three messages.
 *
 * Mosh's `.proto` files describe six messages in total, all of them flat, and the only wire types
 * that ever appear are varint and length-delimited. That is small enough to read and write by
 * hand, which keeps a code generator and a runtime library out of the build for the sake of a few
 * hundred bytes of parsing. The field numbers are in `docs/mosh-wire-format.md` §§2-3.
 *
 * Unknown fields are skipped rather than refused: that is how protocol buffers are meant to behave
 * and it is what would let a future mosh add something without breaking us.
 */
internal class ProtoReader(private val bytes: ByteArray) {

    private var offset = 0

    fun hasMore(): Boolean = offset < bytes.size

    fun readTag(): Int = readVarint().toInt()

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            if (offset >= bytes.size) throw MoshProtocolException("varint runs past the end")
            if (shift > 63) throw MoshProtocolException("varint longer than 64 bits")
            val b = bytes[offset++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
    }

    fun readBytes(): ByteArray {
        val length = readVarint()
        if (length < 0 || length > bytes.size - offset) {
            throw MoshProtocolException("length-delimited field of $length bytes does not fit")
        }
        val out = bytes.copyOfRange(offset, offset + length.toInt())
        offset += length.toInt()
        return out
    }

    fun skip(wireType: Int) {
        when (wireType) {
            WIRE_VARINT -> readVarint()
            WIRE_BYTES -> readBytes()
            WIRE_FIXED64 -> advance(8)
            WIRE_FIXED32 -> advance(4)
            else -> throw MoshProtocolException("wire type $wireType is not one mosh uses")
        }
    }

    private fun advance(n: Int) {
        if (offset + n > bytes.size) throw MoshProtocolException("fixed field runs past the end")
        offset += n
    }

    /** Walks every field in order, handing the caller the field number and its wire type. */
    inline fun forEachField(body: (field: Int, wireType: Int) -> Unit) {
        while (hasMore()) {
            val tag = readTag()
            body(tag ushr 3, tag and 0x7)
        }
    }
}

internal class ProtoWriter {

    private val out = ByteArrayOutputStream()

    fun varint(field: Int, value: Long): ProtoWriter {
        tag(field, WIRE_VARINT)
        putVarint(value)
        return this
    }

    fun bytes(field: Int, value: ByteArray): ProtoWriter {
        tag(field, WIRE_BYTES)
        putVarint(value.size.toLong())
        out.write(value)
        return this
    }

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wireType: Int) = putVarint(((field shl 3) or wireType).toLong())

    private fun putVarint(value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
    }
}

internal const val WIRE_VARINT = 0
internal const val WIRE_FIXED64 = 1
internal const val WIRE_BYTES = 2
internal const val WIRE_FIXED32 = 5
