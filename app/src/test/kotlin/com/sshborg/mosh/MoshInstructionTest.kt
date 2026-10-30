package com.sshborg.mosh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The transport envelope, read from what mosh sent and round-tripped through our own writer. */
class MoshInstructionTest {

    private val realInstruction: MoshInstruction
        get() = MoshInstruction.decode(MoshFragment.decode(MoshPacket.decode(MoshVectors.serverPlaintext).payload).contents)

    @Test fun `mosh's own instruction reads as the source describes it`() {
        val instruction = realInstruction
        assertEquals(MoshInstruction.PROTOCOL_VERSION, instruction.protocolVersion)
        // The captured frame is the server's first: it moves from the state both ends start at.
        assertEquals(0L, instruction.oldNumber)
        assertEquals(1L, instruction.newNumber)
        assertEquals(0L, instruction.ackNumber)
        assertEquals(0L, instruction.throwawayNumber)
        assertEquals("an empty screen has an empty diff", 0, instruction.diff.size)
    }

    @Test fun `a round trip through compression preserves every field`() {
        val original = MoshInstruction(
            oldNumber = 11, newNumber = 12, ackNumber = 9, throwawayNumber = 8,
            diff = ByteArray(300) { (it % 97).toByte() },
        )
        val back = MoshInstruction.decode(original.encode())
        assertEquals(original.oldNumber, back.oldNumber)
        assertEquals(original.newNumber, back.newNumber)
        assertEquals(original.ackNumber, back.ackNumber)
        assertEquals(original.throwawayNumber, back.throwawayNumber)
        assertArrayEquals(original.diff, back.diff)
    }

    @Test fun `large state numbers survive, since they are what the protocol counts with`() {
        val big = MoshInstruction(oldNumber = 1L shl 40, newNumber = (1L shl 40) + 1)
        val back = MoshInstruction.parse(big.serialize())
        assertEquals(1L shl 40, back.oldNumber)
    }

    @Test fun `a different protocol version is refused instead of guessed at`() {
        val wrong = MoshInstruction(protocolVersion = 3)
        try {
            MoshInstruction.decode(wrong.encode())
            fail("version 3 is not a protocol we know")
        } catch (e: MoshProtocolException) {
            assertTrue(e.message!!.contains("protocol version 3"))
        }
    }

    @Test fun `padding and unknown fields are skipped, not refused`() {
        // chaff (field 7) is padding mosh may send; anything newer must not cost us the frame.
        val serialized = ProtoWriter()
            .varint(1, MoshInstruction.PROTOCOL_VERSION.toLong())
            .varint(2, 4).varint(3, 5).varint(4, 1).varint(5, 0)
            .bytes(7, ByteArray(16))
            .varint(99, 1234)
            .toByteArray()
        val back = MoshInstruction.parse(serialized)
        assertEquals(4L, back.oldNumber)
        assertEquals(5L, back.newNumber)
    }

    @Test fun `something that is not zlib is refused cleanly`() {
        try {
            MoshInstruction.decode(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
            fail("that is not compressed data")
        } catch (e: MoshProtocolException) {
            assertTrue(e.message!!.contains("zlib") || e.message!!.contains("truncated"))
        }
    }

    @Test fun `a payload that decompresses absurdly is refused before it is allocated`() {
        // A few kilobytes on the wire that would become five megabytes: the ceiling exists so a
        // hostile or broken peer cannot make us allocate on command.
        val bomb = MoshInstruction.deflate(ByteArray(5_000_000))
        assertTrue("the bomb must be small on the wire", bomb.size < 100_000)
        try {
            MoshInstruction.decode(bomb)
            fail("five megabytes is past the ceiling")
        } catch (e: MoshProtocolException) {
            assertTrue(e.message!!.contains("decompresses beyond"))
        }
    }
}
