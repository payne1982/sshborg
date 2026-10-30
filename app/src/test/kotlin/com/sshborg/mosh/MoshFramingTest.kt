package com.sshborg.mosh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Timestamps, fragment headers and reassembly, checked against a plaintext mosh really sent. */
class MoshFramingTest {

    @Test fun `mosh's plaintext decodes into timestamps and one final fragment`() {
        val packet = MoshPacket.decode(MoshVectors.serverPlaintext)
        // The server had heard from us, so it echoed our timestamp back: that is the round trip.
        assertNotEquals(MoshPacket.NONE, packet.timestampReply)

        val fragment = MoshFragment.decode(packet.payload)
        assertEquals(1L, fragment.id)
        assertEquals(0, fragment.number)
        assertTrue("a short instruction travels in one piece", fragment.final)
    }

    @Test fun `re-encoding mosh's plaintext gives back the same bytes`() {
        val packet = MoshPacket.decode(MoshVectors.serverPlaintext)
        val fragment = MoshFragment.decode(packet.payload)
        assertArrayEquals(packet.payload, fragment.encode())
        assertArrayEquals(MoshVectors.serverPlaintext, packet.encode())
    }

    @Test fun `a packet without room for its timestamps is refused`() {
        try {
            MoshPacket.decode(ByteArray(3))
            org.junit.Assert.fail("three bytes cannot carry two timestamps")
        } catch (e: MoshProtocolException) {
            assertTrue(e.message!!.contains("timestamps"))
        }
    }

    @Test fun `a long instruction is split and put back together`() {
        val diff = ByteArray(4000) { (it % 251).toByte() }
        val instruction = MoshInstruction(oldNumber = 7, newNumber = 8, diff = diff)
        val fragments = MoshFragmenter().fragments(instruction, budget = 200)
        assertTrue("4000 bytes do not fit in one 200-byte fragment", fragments.size > 1)
        assertTrue("only the last piece is final", fragments.dropLast(1).none { it.final })
        assertTrue(fragments.last().final)

        val assembly = MoshFragmentAssembly()
        var payload: ByteArray? = null
        for (fragment in fragments) {
            payload = assembly.add(MoshFragment.decode(fragment.encode()))
        }
        assertArrayEquals(diff, MoshInstruction.decode(payload!!).diff)
    }

    @Test fun `a repeated fragment does not hand the instruction over twice`() {
        val instruction = MoshInstruction(diff = ByteArray(500) { 1 })
        val fragments = MoshFragmenter().fragments(instruction, budget = 100)
        val assembly = MoshFragmentAssembly()
        // Out of order, and with one repeated: the id is what ties them together, and a duplicate
        // must not be counted twice or the assembly would complete one piece early.
        val shuffled = fragments.reversed() + fragments.last()
        var completed = 0
        for (fragment in shuffled) {
            if (assembly.add(fragment) != null) completed++
        }
        assertEquals("exactly one completion", 1, completed)
    }

    @Test fun `a fragment of a different instruction abandons the one in progress`() {
        val assembly = MoshFragmentAssembly()
        assertNull(assembly.add(MoshFragment(id = 1, number = 0, final = false, contents = byteArrayOf(1))))
        // The sender moved on; what we were holding is of no use any more.
        val done = assembly.add(MoshFragment(id = 2, number = 0, final = true, contents = byteArrayOf(2)))
        assertArrayEquals(byteArrayOf(2), done)
    }

    @Test fun `a count of pieces is not the same as having them`() {
        val assembly = MoshFragmentAssembly()
        // Piece 2 of an instruction whose end has not arrived: the total is still unknown.
        assertNull(assembly.add(MoshFragment(id = 9, number = 2, final = false, contents = byteArrayOf(2))))
        // Then a piece that says it is the last and is numbered 1. Two pieces have arrived and the
        // total is two, so the count agrees — on top of a hole where piece 0 should be, which the
        // assembly then walked into. Under our key, so it needs a server that is hostile or
        // broken; either way the answer is a protocol error and not a crash in the session loop.
        try {
            assembly.add(MoshFragment(id = 9, number = 1, final = true, contents = byteArrayOf(1)))
            fail("a total that leaves a piece above it should be refused")
        } catch (e: MoshProtocolException) {
            assertTrue("the reason should name the piece: ${e.message}", e.message!!.contains("past its last one"))
        }
    }

    @Test fun `two different answers about which piece is the last are refused`() {
        val assembly = MoshFragmentAssembly()
        assertNull(assembly.add(MoshFragment(id = 4, number = 0, final = false, contents = byteArrayOf(0))))
        try {
            assembly.add(MoshFragment(id = 4, number = 5, final = true, contents = byteArrayOf(5)))
            assembly.add(MoshFragment(id = 4, number = 9, final = true, contents = byteArrayOf(9)))
            fail("one id cannot have two last fragments")
        } catch (e: MoshProtocolException) {
            assertTrue(e.message!!.contains("two different last fragments"))
        }
    }

    @Test fun `pieces arriving out of order still assemble`() {
        // The rules above must not have cost this: reordering is ordinary on UDP, and the last
        // piece arriving first is how the total becomes known early.
        // Random bytes, because the fragmenter splits the *compressed* instruction and anything
        // with a pattern in it deflates down to one piece.
        val diff = ByteArray(2000).also { java.util.Random(7).nextBytes(it) }
        val instruction = MoshInstruction(oldNumber = 1, newNumber = 2, diff = diff)
        val fragments = MoshFragmenter().fragments(instruction, budget = 120)
        assertTrue("the test needs several pieces", fragments.size >= 4)
        val assembly = MoshFragmentAssembly()
        var payload: ByteArray? = null
        for (fragment in listOf(fragments.last()) + fragments.dropLast(1).reversed()) {
            payload = assembly.add(fragment) ?: payload
        }
        assertArrayEquals(instruction.diff, MoshInstruction.decode(payload!!).diff)
    }

    @Test fun `the id advances for a new instruction and holds for a retransmission`() {
        val fragmenter = MoshFragmenter()
        val first = MoshInstruction(oldNumber = 1, newNumber = 2)
        val idOfFirst = fragmenter.fragments(first, budget = 500).first().id
        // The same instruction again is a retransmission, and mosh reuses the id for it so the far
        // end recognises it instead of starting a fresh assembly.
        assertEquals(idOfFirst, fragmenter.fragments(first, budget = 500).first().id)
        val next = MoshInstruction(oldNumber = 2, newNumber = 3)
        assertNotEquals(idOfFirst, fragmenter.fragments(next, budget = 500).first().id)
    }

    @Test(timeout = 20_000) fun `an instruction refused for its size stops being held`() {
        // A ceiling that is noticed is not a ceiling that is kept. The piece is stored, the total
        // is raised, and only then does the complaint go out — and throwing does not undo any of
        // it. The id is still current, nothing is marked refused, so the next piece is stored too
        // and complained about again: the sender is told it is over budget as many times as it
        // likes while the budget goes on being spent. The caller cannot help, because counting
        // the error and going on reading is the whole right response to one unusable datagram.
        val assembly = MoshFragmentAssembly()
        val piece = ByteArray(1100)
        var refusals = 0
        for (number in 0 until 5000) {
            try {
                assembly.add(MoshFragment(id = 77, number = number, final = false, contents = piece))
            } catch (_: MoshProtocolException) {
                refusals++
            }
            assertTrue(
                "nothing may be held above the ceiling, on any arrival: ${assembly.bytesHeld}",
                assembly.bytesHeld <= MoshFragmentAssembly.MAX_HELD_BYTES,
            )
        }
        assertTrue("a flood of this size has to have been refused", refusals > 0)
        assertEquals("a refused instruction holds nothing at all", 0, assembly.bytesHeld)
        // And its own last fragment does not bring it back: the pieces are gone, so there is no
        // instruction here that could honestly be handed over.
        try {
            assembly.add(MoshFragment(id = 77, number = 5000, final = true, contents = piece))
            fail("a refused instruction cannot be completed")
        } catch (_: MoshProtocolException) {
        }
        assertEquals(0, assembly.bytesHeld)
    }

    @Test(timeout = 20_000) fun `the sender moving on clears a refusal`() {
        // The refusal belongs to the instruction, not to the session: one over-sized instruction
        // must not cost every instruction after it.
        val assembly = MoshFragmentAssembly()
        val piece = ByteArray(1100)
        var number = 0
        while (number < 0x7FFF) {
            try {
                assembly.add(MoshFragment(id = 1, number = number++, final = false, contents = piece))
            } catch (_: MoshProtocolException) {
                break
            }
        }
        assertTrue("the flood should have been refused long before the numbers ran out", number < 0x7FFF)
        val whole = assembly.add(MoshFragment(id = 2, number = 0, final = true, contents = byteArrayOf(1, 2, 3)))
        assertArrayEquals(byteArrayOf(1, 2, 3), whole)
    }
}
