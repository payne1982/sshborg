package com.sshborg.mosh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
}
