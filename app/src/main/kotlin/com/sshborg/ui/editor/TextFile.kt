package com.sshborg.ui.editor

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

/**
 * Turning a remote file's bytes into text the editor can show, and back again byte for byte.
 *
 * The round trip is the whole point: whatever the user does not touch must go back to the
 * server exactly as it arrived. Nothing is guessed silently — a charset is only ever used once
 * decoding it and re-encoding the result has been shown to give back the original bytes, so a
 * wrong guess cannot corrupt the file, it can only look wrong on screen. What the user does see
 * they can correct: the editor lets them pick another charset and reads the same bytes again.
 *
 * The only thing normalised on the way in is the line ending, which is put back on the way out;
 * nothing is added, and a file that ended without a newline still ends without one after saving.
 */
object TextFile {

    enum class LineEnding(val text: String) { LF("\n"), CRLF("\r\n") }

    /** A byte-order mark: the one part of the encoding a file states about itself. */
    enum class Bom(val bytes: ByteArray, val charsetName: String) {
        UTF_32LE(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0, 0), "UTF-32LE"),
        UTF_32BE(byteArrayOf(0, 0, 0xFE.toByte(), 0xFF.toByte()), "UTF-32BE"),
        UTF_8(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), "UTF-8"),
        // Must come after the UTF-32 marks, whose first two bytes are the same.
        UTF_16LE(byteArrayOf(0xFF.toByte(), 0xFE.toByte()), "UTF-16LE"),
        UTF_16BE(byteArrayOf(0xFE.toByte(), 0xFF.toByte()), "UTF-16BE"),
    }

    /** A file we can edit as text: [text] plus everything needed to rebuild the original bytes. */
    data class Decoded(
        val text: String,
        val charset: Charset,
        /** Present in the file and kept out of [text]; written back untouched. */
        val bom: Bom?,
        val lineEnding: LineEnding,
        /** The file mixed LF and CRLF; saving settles on [lineEnding] for the whole file. */
        val mixedEndings: Boolean,
    ) {
        val label: String get() = bom?.let { "${charset.name()} BOM" } ?: charset.name()
    }

    /**
     * Every charset the picker can offer. Order here is for the list, not for guessing.
     */
    private val ALL = listOf(
        "UTF-8", "UTF-16LE", "UTF-16BE", "ISO-8859-15", "windows-1252", "ISO-8859-1",
        "windows-1250", "ISO-8859-2", "windows-1251", "KOI8-R", "KOI8-U", "ISO-8859-5",
        "windows-1253", "ISO-8859-7", "windows-1254", "ISO-8859-9", "windows-1257",
        "windows-1255", "windows-1256", "GB18030", "GBK", "Big5", "Shift_JIS", "EUC-JP",
        "EUC-KR",
    )

    /** Charsets to try first for a given device language, before the rest of [CANDIDATES]. */
    private val BY_LANGUAGE = mapOf(
        "ru" to listOf("windows-1251", "KOI8-R", "ISO-8859-5"),
        "uk" to listOf("windows-1251", "KOI8-U", "KOI8-R"),
        "bg" to listOf("windows-1251", "ISO-8859-5"),
        "zh" to listOf("GB18030", "GBK", "Big5"),
        "ja" to listOf("Shift_JIS", "EUC-JP"),
        "ko" to listOf("EUC-KR"),
        "el" to listOf("windows-1253", "ISO-8859-7"),
        "tr" to listOf("windows-1254", "ISO-8859-9"),
        "pl" to listOf("windows-1250", "ISO-8859-2"),
        "cs" to listOf("windows-1250", "ISO-8859-2"),
        "hu" to listOf("windows-1250", "ISO-8859-2"),
        "he" to listOf("windows-1255"),
        "ar" to listOf("windows-1256"),
    )

    /**
     * Whether these bytes are not text, and so can only be shown in the hex view.
     *
     * A NUL byte settles it — no single-byte text file has one, every binary format does.
     * Failing that we look at the control characters: a handful means an odd file, a quarter of
     * it means a binary that happens to have no NUL in it. UTF-16 and UTF-32 are full of NULs,
     * which is why they are recognised by their mark before this is ever asked.
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

    /**
     * Reads [bytes] as text, or returns null if they are not text at all.
     *
     * A byte-order mark decides on its own. Otherwise the candidates are tried in order and the
     * first one whose round trip is exact wins, the ones suggested by [locale] first — the
     * phone's language is a better hint than anything the bytes can say, since the same bytes
     * are legal Cyrillic and legal Greek.
     */
    fun decode(bytes: ByteArray, locale: Locale = Locale.getDefault()): Decoded? {
        bomOf(bytes)?.let { bom ->
            val charset = charsetOrNull(bom.charsetName) ?: return@let
            return decodeWith(bytes, charset, bom)
        }
        // Unmarked UTF-16 is the one text format the control-character test would throw away,
        // so it is asked about first, on the evidence of its alternating NUL bytes.
        unmarkedUtf16(bytes)?.let { return it }
        if (isBinary(bytes)) return null
        for (charset in candidates(locale, bytes)) {
            decodeWith(bytes, charset, null)?.let { return it }
        }
        return null
    }

    /**
     * Reads [bytes] as [charset], or returns null if that would not survive the way back:
     * either the bytes are not valid in it, or re-encoding what came out gives different bytes.
     * Verifying instead of trusting costs one pass over a file this small, and it is what makes
     * an unknown charset safe to open — if the round trip is exact, saving cannot corrupt the
     * parts the user never touched, whatever the charset turns out to really be.
     */
    fun decodeWith(bytes: ByteArray, charset: Charset, bom: Bom?): Decoded? {
        val body = if (bom != null) bytes.copyOfRange(bom.bytes.size, bytes.size) else bytes
        val raw = strictDecode(body, charset) ?: return null
        val backAgain = runCatching { strictEncode(raw, charset) }.getOrNull()
        if (backAgain == null || !backAgain.contentEquals(body)) return null

        val crlf = countOf(raw, "\r\n")
        val lf = raw.count { it == '\n' } - crlf
        return Decoded(
            text = if (crlf > 0) raw.replace("\r\n", "\n") else raw,
            charset = charset,
            bom = bom,
            lineEnding = if (crlf > lf) LineEnding.CRLF else LineEnding.LF,
            mixedEndings = crlf > 0 && lf > 0,
        )
    }

    /**
     * The charsets [bytes] could be read as, for the picker: every candidate whose round trip is
     * exact, best guess first. Anything left out would not survive being saved, so it is not
     * offered at all rather than offered as a trap.
     */
    fun readableAs(bytes: ByteArray, locale: Locale = Locale.getDefault()): List<Charset> {
        val order = candidates(locale, bytes) + ALL.mapNotNull { charsetOrNull(it) }
        return order.distinct().filter { decodeWith(bytes, it, bomOf(bytes)) != null }
    }

    /**
     * Rebuilds the file's bytes from edited [text], restoring [from]'s ending, mark and charset.
     * Returns null if the text now holds characters that charset cannot write — typing a € into
     * a Latin-1 file, say — which is a thing to say out loud, not to replace with a "?".
     */
    fun encode(text: String, from: Decoded): ByteArray? {
        val withEndings = if (from.lineEnding == LineEnding.CRLF) text.replace("\n", "\r\n") else text
        val body = try {
            strictEncode(withEndings, from.charset)
        } catch (_: Exception) {
            return null
        }
        if (strictDecode(body, from.charset) != withEndings) return null
        return if (from.bom != null) from.bom.bytes + body else body
    }

    /**
     * What is tried automatically, in order — and deliberately short.
     *
     * Only evidence belongs here. UTF-8 either decodes or it does not, and that is a fact about
     * the bytes. Everything after it is a guess, so the only guesses made are the ones with a
     * reason behind them: the phone's language, and then Latin, which is what a file on a
     * European server that is not UTF-8 nearly always is.
     *
     * The rest of [ALL] is left out on purpose. A single-byte charset accepts *any* bytes, so
     * windows-1251 in this list would claim every Italian file before ISO-8859-15 was ever
     * tried and show Cyrillic where there are accents; GB18030 accepts nearly any bytes too and
     * would claim the Japanese and Taiwanese files along with them. Both really happened here.
     * Without a reason to prefer one, the machine cannot tell them apart — only the reader can,
     * which is what the picker is for.
     */
    private fun candidates(locale: Locale, bytes: ByteArray): List<Charset> {
        val latin = if (hasC1(bytes)) listOf("windows-1252", "ISO-8859-15")
                    else listOf("ISO-8859-15", "windows-1252")
        val names = listOf("UTF-8") + BY_LANGUAGE[locale.language].orEmpty() + latin + "ISO-8859-1"
        return names.distinct().mapNotNull { charsetOrNull(it) }
    }

    /**
     * Whether any byte falls in 0x80-0x9F. ISO-8859 puts control characters there and would
     * show nothing at all, while the Windows code pages put the quotes and dashes that Windows
     * editors actually write — so a file using that range is read as windows-1252 first.
     */
    private fun hasC1(bytes: ByteArray): Boolean = bytes.any {
        val b = it.toInt() and 0xFF
        b in 0x80..0x9F
    }

    /** UTF-16 with no mark: every other byte is NUL in plain text, and the side says which one. */
    private fun unmarkedUtf16(bytes: ByteArray): Decoded? {
        if (bytes.size < 4 || bytes.size % 2 != 0) return null
        var evenNuls = 0
        var oddNuls = 0
        val sample = minOf(bytes.size, 2048)
        for (i in 0 until sample) {
            if (bytes[i].toInt() == 0) if (i % 2 == 0) evenNuls++ else oddNuls++
        }
        val name = when {
            oddNuls * 4 > sample && evenNuls == 0 -> "UTF-16LE"
            evenNuls * 4 > sample && oddNuls == 0 -> "UTF-16BE"
            else -> return null
        }
        val charset = charsetOrNull(name) ?: return null
        return decodeWith(bytes, charset, null)
    }

    private fun bomOf(bytes: ByteArray): Bom? = Bom.entries.firstOrNull { bom ->
        bytes.size >= bom.bytes.size && bom.bytes.indices.all { bytes[it] == bom.bytes[it] }
    }

    private fun charsetOrNull(name: String): Charset? =
        runCatching { if (Charset.isSupported(name)) Charset.forName(name) else null }.getOrNull()

    private fun strictDecode(bytes: ByteArray, charset: Charset): String? {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return runCatching { decoder.decode(ByteBuffer.wrap(bytes)).toString() }.getOrNull()
    }

    private fun strictEncode(text: String, charset: Charset): ByteArray {
        val encoder = charset.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val buffer = encoder.encode(java.nio.CharBuffer.wrap(text))
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
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
