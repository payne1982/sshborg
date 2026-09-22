package com.sshborg.ui.editor

import java.nio.ByteBuffer
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction

/**
 * Turning a remote file's bytes into text the editor can show, and back again byte for byte.
 *
 * The round trip is the whole point: whatever we do not touch must go back to the server
 * exactly as it arrived. So the only thing normalised on the way in is the line ending, which
 * is put back on the way out; nothing is added, and in particular a file that ended without a
 * newline still ends without one after saving.
 */
object TextFile {

    /** How the bytes were read. ISO-8859-1 maps every byte one to one, so it never loses any. */
    enum class Encoding { UTF_8, LATIN_1 }

    enum class LineEnding(val text: String) { LF("\n"), CRLF("\r\n") }

    /** A file we can edit as text: [text] plus everything needed to rebuild the original bytes. */
    data class Decoded(
        val text: String,
        val encoding: Encoding,
        val lineEnding: LineEnding,
        /** A UTF-8 byte-order mark was present; it is kept out of [text] and put back on save. */
        val bom: Boolean,
        /** The file mixed LF and CRLF; saving settles on [lineEnding] for the whole file. */
        val mixedEndings: Boolean,
    )

    private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    /**
     * Whether these bytes are not text, and so can only be shown in the hex view.
     *
     * A NUL byte settles it — no text file has one, every binary format does. Failing that we
     * look at the control characters: a handful means an odd file, a quarter of it means a
     * binary that happens to have no NUL in it (some compressed and image formats).
     */
    fun isBinary(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val sample = minOf(bytes.size, 8 * 1024)
        var controls = 0
        for (i in 0 until sample) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0) return true
            if (b < 0x20 && b != 0x09 && b != 0x0A && b != 0x0D) controls++
        }
        return controls * 4 > sample
    }

    /** Decodes [bytes] for editing. Call [isBinary] first — this assumes text. */
    fun decode(bytes: ByteArray): Decoded {
        val bom = bytes.size >= 3 && BOM.indices.all { bytes[it] == BOM[it] }
        val body = if (bom) bytes.copyOfRange(3, bytes.size) else bytes

        val encoding = if (isValidUtf8(body)) Encoding.UTF_8 else Encoding.LATIN_1
        val raw = String(body, charset(encoding))

        val crlf = countOf(raw, "\r\n")
        val lf = raw.count { it == '\n' } - crlf
        return Decoded(
            text = if (crlf > 0) raw.replace("\r\n", "\n") else raw,
            encoding = encoding,
            lineEnding = if (crlf > lf) LineEnding.CRLF else LineEnding.LF,
            bom = bom,
            mixedEndings = crlf > 0 && lf > 0,
        )
    }

    /** Rebuilds the file's bytes from edited [text], restoring [from]'s ending, BOM and charset. */
    fun encode(text: String, from: Decoded): ByteArray {
        val withEndings = if (from.lineEnding == LineEnding.CRLF) text.replace("\n", "\r\n") else text
        val body = withEndings.toByteArray(charset(from.encoding))
        return if (from.bom) BOM + body else body
    }

    private fun charset(encoding: Encoding) =
        if (encoding == Encoding.UTF_8) Charsets.UTF_8 else Charsets.ISO_8859_1

    private fun isValidUtf8(bytes: ByteArray): Boolean {
        val decoder: CharsetDecoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return runCatching { decoder.decode(ByteBuffer.wrap(bytes)) }.isSuccess
    }

    private fun countOf(s: String, sub: String): Int {
        var n = 0
        var i = s.indexOf(sub)
        while (i >= 0) {
            n++
            i = s.indexOf(sub, i + sub.length)
        }
        return n
    }
}
