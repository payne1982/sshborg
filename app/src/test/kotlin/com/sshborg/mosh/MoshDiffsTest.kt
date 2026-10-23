package com.sshborg.mosh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two state diffs. The host side is read from a diff mosh really sent — the redraw caused by
 * typing a command into a live session — and the user side is checked against its exact wire
 * layout, because a wrong field number is the realistic mistake here and it would fail silently:
 * the server would accept the frame and simply ignore the keystrokes.
 */
class MoshDiffsTest {

    @Test fun `a real host diff reads as one piece of terminal output`() {
        val actions = MoshDiffs.decodeHostDiff(MoshVectors.hostDiff)
        assertEquals(1, actions.size)
        val bytes = (actions.single() as MoshDiffs.HostAction.HostBytes).bytes
        val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue("the redraw hides the cursor first: $text", text.startsWith("\u001B[?25l"))
        assertTrue("it carries what was typed", text.contains("echo mosh-lab-ok"))
        assertTrue("and shows it again at the end", text.endsWith("\u001B[?25h"))
    }

    @Test fun `keystrokes are written where mosh looks for them`() {
        val encoded = MoshDiffs.encodeUserDiff(listOf(MoshDiffs.UserAction.Keystrokes("hi".toByteArray())))
        // instruction(1) { keystroke(2) { keys(4) = "hi" } }
        assertArrayEquals(
            byteArrayOf(0x0A, 0x06, 0x12, 0x04, 0x22, 0x02, 'h'.code.toByte(), 'i'.code.toByte()),
            encoded,
        )
    }

    @Test fun `a resize is written where mosh looks for it`() {
        val encoded = MoshDiffs.encodeUserDiff(listOf(MoshDiffs.UserAction.Resize(80, 24)))
        // instruction(1) { resize(3) { width(5) = 80, height(6) = 24 } }
        assertArrayEquals(byteArrayOf(0x0A, 0x06, 0x1A, 0x04, 0x28, 80, 0x30, 24), encoded)
    }

    @Test fun `several actions keep the order they happened in`() {
        val encoded = MoshDiffs.encodeUserDiff(
            listOf(
                MoshDiffs.UserAction.Keystrokes(byteArrayOf('a'.code.toByte())),
                MoshDiffs.UserAction.Resize(100, 40),
                MoshDiffs.UserAction.Keystrokes(byteArrayOf('b'.code.toByte())),
            ),
        )
        // Three instructions under field 1, in the order they were given: keystroke, resize,
        // keystroke. The server applies them in sequence, so the order is part of the meaning.
        val extensions = ArrayList<Int>()
        val outer = ProtoReader(encoded)
        outer.forEachField { field, wireType ->
            assertEquals(1, field)
            val instruction = ProtoReader(outer.readBytes())
            instruction.forEachField { ext, extWire ->
                extensions.add(ext)
                instruction.skip(extWire)
            }
            assertEquals(WIRE_BYTES, wireType)
        }
        assertEquals(listOf(2, 3, 2), extensions)
    }

    @Test fun `resize and echo ack are recognised in a host diff`() {
        // Built the way the server builds them, since the capture happened to contain neither.
        val resize = ProtoWriter().varint(5, 132).varint(6, 43).toByteArray()
        val echo = ProtoWriter().varint(8, 7).toByteArray()
        val diff = ProtoWriter()
            .bytes(1, ProtoWriter().bytes(3, resize).toByteArray())
            .bytes(1, ProtoWriter().bytes(7, echo).toByteArray())
            .toByteArray()

        val actions = MoshDiffs.decodeHostDiff(diff)
        assertEquals(2, actions.size)
        val size = actions[0] as MoshDiffs.HostAction.Resize
        assertEquals(132, size.width)
        assertEquals(43, size.height)
        assertEquals(7L, (actions[1] as MoshDiffs.HostAction.EchoAck).number)
    }

    @Test fun `an extension we do not know does not cost us the ones we do`() {
        val unknown = ProtoWriter().varint(42, 1).toByteArray()
        val diff = ProtoWriter()
            .bytes(1, ProtoWriter().bytes(9, unknown).toByteArray())
            .bytes(1, ProtoWriter().bytes(2, ProtoWriter().bytes(4, "ok".toByteArray()).toByteArray()).toByteArray())
            .toByteArray()

        val actions = MoshDiffs.decodeHostDiff(diff)
        assertEquals(1, actions.size)
        assertEquals("ok", String((actions.single() as MoshDiffs.HostAction.HostBytes).bytes))
    }
}
