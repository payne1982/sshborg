package com.sshborg.data.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What counts as the pinned key changing, and what is kept when a new one is accepted. */
class KnownHostsTest {

    private val pinned = "fatberry ssh-ed25519 AAAAoriginal"

    @Test fun `the same key is not a change`() {
        assertFalse(KnownHosts.identityChanged(pinned, "fatberry ssh-ed25519 AAAAoriginal"))
    }

    @Test fun `another key for the same algorithm is a change`() {
        assertTrue(KnownHosts.identityChanged(pinned, "fatberry ssh-ed25519 AAAAsomethingelse"))
    }

    @Test fun `a host we have pinned nothing for has not changed`() {
        assertFalse(KnownHosts.identityChanged(null, "fatberry ssh-ed25519 AAAAoriginal"))
        assertFalse(KnownHosts.identityChanged("", "fatberry ssh-ed25519 AAAAoriginal"))
    }

    @Test fun `a second algorithm is a key we have not seen, not a key that changed`() {
        // What `ssh` does too: the host has an identity we have no opinion about yet.
        assertFalse(KnownHosts.identityChanged(pinned, "fatberry ssh-rsa AAAArsa"))
    }

    @Test fun `a line that no longer names what we dialled is stale, not evidence`() {
        // The row's hostname or port was edited, so JSch found nothing under the new name and is
        // asking as it would for any new host. Warning about a change here would be a guess.
        assertFalse(KnownHosts.identityChanged(pinned, "maxmaster ssh-ed25519 AAAAdifferent"))
        assertFalse(KnownHosts.identityChanged("[fatberry]:2222 ssh-ed25519 AAAAoriginal", "fatberry ssh-ed25519 AAAAdifferent"))
    }

    @Test fun `several names on one line all count as that line's host`() {
        assertTrue(KnownHosts.identityChanged("fatberry,10.0.0.9 ssh-ed25519 AAAAoriginal", "10.0.0.9 ssh-ed25519 AAAAnew"))
    }

    @Test fun `accepting replaces the key for that algorithm and keeps the rest`() {
        val entry = "fatberry ssh-ed25519 AAAAoriginal\nfatberry ssh-rsa AAAArsa"
        assertEquals(
            "fatberry ssh-rsa AAAArsa\nfatberry ssh-ed25519 AAAAnew",
            KnownHosts.replacing(entry, "fatberry ssh-ed25519 AAAAnew"),
        )
    }

    @Test fun `accepting a first key pins it`() {
        assertEquals("fatberry ssh-ed25519 AAAAnew", KnownHosts.replacing(null, "fatberry ssh-ed25519 AAAAnew"))
    }

    @Test fun `accepting a second algorithm keeps the first`() {
        assertEquals(
            "fatberry ssh-ed25519 AAAAoriginal\nfatberry ssh-rsa AAAArsa",
            KnownHosts.replacing(pinned, "fatberry ssh-rsa AAAArsa"),
        )
    }

    @Test fun `another host's line is not touched`() {
        val entry = "maxmaster ssh-ed25519 AAAAmax\nfatberry ssh-ed25519 AAAAoriginal"
        assertEquals(
            "maxmaster ssh-ed25519 AAAAmax\nfatberry ssh-ed25519 AAAAnew",
            KnownHosts.replacing(entry, "fatberry ssh-ed25519 AAAAnew"),
        )
    }

    @Test fun `a key already pinned is not collected again`() {
        // What a hop asks after it has connected. Everything else — nothing pinned, a second
        // algorithm, a key just accepted in place of the old one — is worth storing.
        assertTrue(KnownHosts.isPinned(pinned, "fatberry ssh-ed25519 AAAAoriginal"))
        assertFalse(KnownHosts.isPinned(pinned, "fatberry ssh-ed25519 AAAAnew"))
        assertFalse(KnownHosts.isPinned(pinned, "fatberry ssh-rsa AAAArsa"))
        assertFalse(KnownHosts.isPinned(null, "fatberry ssh-ed25519 AAAAoriginal"))
    }

    @Test fun `a hop's line in the chain's blob gives way without disturbing the other hops`() {
        // The hand-written chain keeps every hop's key in one blob on the target's row. Appending
        // the accepted line beside the old one would leave the old key trusted for good.
        val blob = "hop1 ssh-ed25519 AAAAone\nhop2 ssh-ed25519 AAAAtwo\nhop3 ssh-ed25519 AAAAthree"
        assertEquals(
            "hop1 ssh-ed25519 AAAAone\nhop3 ssh-ed25519 AAAAthree\nhop2 ssh-ed25519 AAAAnew",
            KnownHosts.replacing(blob, "hop2 ssh-ed25519 AAAAnew"),
        )
        assertTrue(KnownHosts.identityChanged(blob, "hop2 ssh-ed25519 AAAAnew"))
        assertFalse(KnownHosts.identityChanged(blob, "hop4 ssh-ed25519 AAAAfour"))
    }
}
