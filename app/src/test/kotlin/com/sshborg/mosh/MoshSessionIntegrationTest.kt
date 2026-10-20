package com.sshborg.mosh

import com.sshborg.TestConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Phase 4's gate: a session with a real `mosh-server`, driven by the code the app would use.
 *
 * It skips itself unless a server has been started for it — `scripts/mosh-lab.sh` does that, runs
 * this, and takes the server down again. A green unit suite proves the pieces; only this proves
 * that mosh agrees with us about them.
 *
 * Four things are checked, in the one session, because they are only interesting together and
 * because the closing handshake makes `mosh-server` exit — so there is only ever one session per
 * run to check them in. Typing reaches a shell and its output comes back; the session survives
 * being idle through several heartbeats; it survives **the local port being replaced underneath
 * it**, which is the mobile case the whole feature exists for (a phone moving from Wi-Fi to
 * cellular gets a new source address, and the sequence counter carrying on across that is what
 * lets the server follow); and a repaint too big for one datagram arrives in several fragments
 * and goes back together.
 */
class MoshSessionIntegrationTest {

    private val configured = TestConfig.moshServer()

    @Test fun `a session types, idles, survives a new local port and reassembles a big repaint`() {
        assumeTrue(TestConfig.NO_MOSH_SERVER, configured != null)
        val session = MoshLabSession(configured!!)
        try {
            session.resize(80, 24)

            session.type("echo mosh-phase4-ok\r")
            assertTrue("the shell never answered", session.awaitOutput("mosh-phase4-ok", 10_000))

            // Nothing typed for longer than the heartbeat interval: the server must still be
            // talking to us afterwards, which it would not be if the heartbeat were not going out.
            session.idle(7_000)
            assertTrue("the server stopped answering while idle", session.heardFromServerWithin(4_000))

            // The phone changed network. Same session, same key, same sequence counter, new socket.
            session.rebindLocalPort()
            session.type("echo mosh-after-the-hop\r")
            assertTrue(
                "the session did not survive the change of local port",
                session.awaitOutput("mosh-after-the-hop", 15_000),
            )

            // A repaint larger than one datagram. Nothing the 80x24 shell above does ever comes
            // close: its frames are tens of bytes compressed, against a budget of 1242 per
            // fragment. So the window is made big and painted with base64 of random bytes, which
            // deflate cannot squeeze below about three quarters of its size.
            //
            // The assertion is only half the check. The reassembled payload is a zlib stream, so
            // its Adler-32 is verified on the way through MoshInstruction.decode: fragments put
            // together in the wrong order, or with one counted twice, do not inflate. Arriving at
            // a frame we could parse *is* the proof that the reassembly was right.
            session.resize(240, 70)
            session.type("head -c 40000 /dev/urandom | base64 | tr -d '\\n' | fold -w 230 | head -n 60\r")
            assertTrue(
                "no instruction ever needed more than one datagram (largest assembly: " +
                    session.largestAssembly + ")",
                session.awaitLargeAssembly(minFragments = 2, timeoutMs = 20_000),
            )

            // The far end must never reuse a fragment id for different bytes: our assembly hands a
            // completed instruction over once and would mix two instructions into one if it did.
            // Mosh's random chaff is what makes that safe, and this is where that is checked
            // against the server rather than taken on trust.
            assertEquals("the server reused a fragment id for different bytes", 0, session.conflictingFragments)

            // printf splits the marker so that the echo of the typed line does not contain it:
            // otherwise the wait below would be satisfied by our own keystrokes coming back.
            session.type("printf 'mosh-%s\\n' after-the-repaint\r")
            assertTrue(
                "the session stopped working after a multi-fragment frame",
                session.awaitOutput("mosh-after-the-repaint", 15_000),
            )

            assertTrue("the closing handshake went unanswered", session.shutdown(5_000))
            assertEquals("datagrams were dropped as unparseable", 0, session.protocolErrors)
        } finally {
            session.close()
        }
    }

}
