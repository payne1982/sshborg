package com.sshborg.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * `OSC 52`, the far end asking to put text on the clipboard (issue #22).
 *
 * Two things are being held here, and only one of them is the decoding.
 *
 * The first is that **the read form is never answered**. `OSC 52` can also ask the terminal to
 * send the clipboard *back* into the session, and the terminal has the means to answer — it
 * replies to device-status requests through the same callback. Anything able to write to a
 * terminal could then walk off with whatever a password manager last put on the clipboard, so the
 * query is ignored and nothing is written back. A test, rather than a comment, because the
 * machinery to get it wrong is right there.
 *
 * The second is that **nothing from the far end can throw**. This runs inside the loop that
 * applies host bytes, and the transports call it without a try/catch of their own: an exception
 * from a malformed payload would travel up and end the session. A line of a log that happens to
 * look like `OSC 52` must cost nothing at all.
 */
class ClipboardOscTest {

    private val emulator = TerminalEmulator(80, 24)
    private var clipboard: String? = null
    private val sentBack = StringBuilder()

    init {
        emulator.onClipboard = { clipboard = it }
        emulator.onSendResponse = { sentBack.append(String(it, Charsets.ISO_8859_1)) }
    }

    private fun feed(text: String) = emulator.process(text.toByteArray(Charsets.ISO_8859_1))

    private fun b64(text: String): String =
        Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

    @Test fun `the clipboard selection is taken`() {
        feed("\u001b]52;c;${b64("hello")}\u0007")
        assertEquals("hello", clipboard)
    }

    @Test fun `an empty selection field is taken too, because that is what tmux sends`() {
        feed("\u001b]52;;${b64("from tmux")}\u0007")
        assertEquals("from tmux", clipboard)
    }

    @Test fun `so is any other selection, rather than being thrown away`() {
        feed("\u001b]52;p;${b64("primary")}\u0007")
        assertEquals("primary", clipboard)
    }

    @Test fun `ST ends the string as well as BEL`() {
        feed("\u001b]52;c;${b64("terminated by ST")}\u001b\\")
        assertEquals("terminated by ST", clipboard)
    }

    @Test fun `the payload is UTF-8, accents and all`() {
        feed("\u001b]52;c;${b64("città — ✦ 日本")}\u0007")
        assertEquals("città — ✦ 日本", clipboard)
    }

    @Test fun `base64 broken across lines still decodes`() {
        // Some senders wrap the payload. The lenient decoder drops what is not in the alphabet.
        val wrapped = b64("a longer line of text than most").chunked(8).joinToString("\r\n")
        feed("\u001b]52;c;$wrapped\u0007")
        assertEquals("a longer line of text than most", clipboard)
    }

    @Test fun `the read query is ignored and nothing is sent back`() {
        feed("\u001b]52;c;?\u0007")
        assertNull("the clipboard must not be read on the far end's asking", clipboard)
        assertEquals("and nothing may be typed back into the session", "", sentBack.toString())
    }

    @Test fun `a payload that is not base64 costs nothing`() {
        feed("\u001b]52;c;not base64 at all!!\u0007")
        // Whatever the decoder makes of the characters it recognises, it must not throw and must
        // not be allowed to pass off rubbish: either nothing or a short string, never a crash.
        feed("\u001b]52;c;${b64("still here")}\u0007")
        assertEquals("still here", clipboard)
    }

    @Test fun `an empty payload is not a clipboard`() {
        feed("\u001b]52;c;\u0007")
        assertNull(clipboard)
    }

    @Test fun `a string with no selection field at all is ignored`() {
        feed("\u001b]52\u0007")
        feed("\u001b]52;\u0007")
        assertNull(clipboard)
    }

    @Test fun `a payload past the limit is dropped, and the parser comes back out`() {
        var title: String? = null
        emulator.onTitleChanged = { title = it }
        feed("\u001b]52;c;" + "A".repeat(TerminalEmulator.MAX_OSC + 100) + "\u0007")
        assertNull("nothing that long is acted on", clipboard)
        feed("\u001b]0;a title\u0007")
        assertEquals("and the next string still works", "a title", title)
    }

    @Test fun `a clipboard sequence leaves the screen alone`() {
        feed("before ")
        feed("\u001b]52;c;${b64("not drawn")}\u0007")
        feed("after")
        assertTrue(emulator.buffer.getRowText(0).startsWith("before after"))
    }
}
