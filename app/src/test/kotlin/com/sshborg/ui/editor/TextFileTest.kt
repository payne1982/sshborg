package com.sshborg.ui.editor

import java.io.File
import java.nio.charset.Charset
import java.util.Locale
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The rule the editor cannot break: what the user did not touch goes back to the server byte
 * for byte. Every test here is that same sentence applied to one awkward file — the ones that
 * broke it while it was being built, and the ones a server is full of.
 */
class TextFileTest {

    private fun roundTrip(bytes: ByteArray, locale: Locale = Locale.ITALIAN): ByteArray {
        val decoded = TextFile.decode(bytes, locale)
        assertNotNull("not read as text at all", decoded)
        val back = TextFile.encode(decoded!!.text, decoded)
        assertNotNull("read but could not be written back", back)
        return back!!
    }

    private fun assertExactRoundTrip(bytes: ByteArray) =
        assertArrayEquals(bytes, roundTrip(bytes))

    @Test fun `utf8 survives`() =
        assertExactRoundTrip("ciao è così — 日本語 🚀\nsecond line\n".toByteArray())

    @Test fun `a file that ends without a newline still does`() =
        assertExactRoundTrip("no trailing newline".toByteArray())

    @Test fun `crlf is kept out of the text and put back`() {
        val bytes = "one\r\ntwo\r\n".toByteArray()
        val decoded = TextFile.decode(bytes)!!
        assertEquals(TextFile.LineEnding.CRLF, decoded.lineEnding)
        assertEquals("one\ntwo\n", decoded.text)
        assertTrue("no CR should reach the editor", !decoded.text.contains('\r'))
        assertArrayEquals(bytes, TextFile.encode(decoded.text, decoded))
    }

    @Test fun `a mixed file is flagged and settles on the majority ending`() {
        val decoded = TextFile.decode("a\r\nb\r\nc\n".toByteArray())!!
        assertTrue(decoded.mixedEndings)
        assertEquals(TextFile.LineEnding.CRLF, decoded.lineEnding)
    }

    @Test fun `a bom is kept out of the text and written back`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = bom + "hello".toByteArray()
        val decoded = TextFile.decode(bytes)!!
        assertEquals(TextFile.Bom.UTF_8, decoded.bom)
        assertEquals("hello", decoded.text)
        assertArrayEquals(bytes, TextFile.encode(decoded.text, decoded))
    }

    @Test fun `unmarked utf16 is recognised, not thrown away as binary`() {
        val bytes = "ciao\n".toByteArray(Charset.forName("UTF-16LE"))
        val decoded = TextFile.decode(bytes)!!
        assertEquals("ciao\n", decoded.text)
        assertArrayEquals(bytes, TextFile.encode(decoded.text, decoded))
    }

    @Test fun `latin bytes that are not utf8 are read as latin and go back unchanged`() {
        // "però" in ISO-8859-15: 0xF2 is not valid UTF-8, so this is the fork in decode().
        val bytes = byteArrayOf(0x70, 0x65, 0x72, 0xF2.toByte(), 0x0A)
        val decoded = TextFile.decode(bytes)!!
        assertEquals("però\n", decoded.text)
        assertArrayEquals(bytes, TextFile.encode(decoded.text, decoded))
    }

    @Test fun `a binary file is refused, and can still be opened on purpose`() {
        val bytes = byteArrayOf(0x68, 0x69, 0x00, 0x01, 0x02, 0x7F, 0x68)
        assertTrue(TextFile.isBinary(bytes))
        assertNull("binary must not be read as text by default", TextFile.decode(bytes))
        val forced = TextFile.decode(bytes, allowBinary = true)
        assertNotNull("opening it on purpose has to work — that is how a stray NUL is repaired", forced)
        assertArrayEquals(bytes, TextFile.encode(forced!!.text, forced))
    }

    @Test fun `a character the charset cannot write is refused, not replaced`() {
        val bytes = byteArrayOf(0x70, 0x65, 0x72, 0xF2.toByte())
        val decoded = TextFile.decodeWith(bytes, Charset.forName("ISO-8859-1"), null)!!
        assertNull("a euro sign in a Latin-1 file must fail loudly", TextFile.encode(decoded.text + "€", decoded))
    }

    @Test fun `the picker only offers charsets that survive the way back`() {
        val bytes = "日本語のファイル\n".toByteArray(Charset.forName("Shift_JIS"))
        val offered = TextFile.readableAs(bytes, Locale.ITALIAN)
        assertTrue("Shift_JIS should be on offer", offered.any { it.name() == "Shift_JIS" })
        offered.forEach {
            assertNotNull("offered but not round-trippable: ${it.name()}", TextFile.decodeWith(bytes, it, null))
        }
    }

    /**
     * The same rule over the real corpus, which holds the files that actually broke this: ten
     * charsets, CRLF and mixed endings, a 20k line, an empty file, executables, text with NULs,
     * and sizes up to 5 MB. Skipped unless SSHBORG_EDITOR_CORPUS points at it, so the suite
     * stays runnable anywhere.
     */
    @Test fun `every file in the corpus goes back unchanged, or is refused`() {
        val dir = System.getenv("SSHBORG_EDITOR_CORPUS")?.let(::File)
        assumeTrue("SSHBORG_EDITOR_CORPUS is not set", dir != null && dir.isDirectory)
        var text = 0
        var binary = 0
        dir!!.walkTopDown().filter { it.isFile }.forEach { file ->
            val bytes = file.readBytes()
            val decoded = TextFile.decode(bytes, Locale.ITALIAN)
            if (decoded == null) {
                binary++
                assertTrue("${file.name}: refused but not binary", TextFile.isBinary(bytes) || bytes.isEmpty())
            } else {
                text++
                val back = TextFile.encode(decoded.text, decoded)
                assertNotNull("${file.name}: read but not writable", back)
                if (!decoded.mixedEndings) {
                    assertArrayEquals("${file.name}: round trip is not byte-exact", bytes, back)
                }
            }
        }
        assertTrue("corpus looks empty", text + binary > 0)
        println("editor corpus: $text text, $binary refused")
    }
}
