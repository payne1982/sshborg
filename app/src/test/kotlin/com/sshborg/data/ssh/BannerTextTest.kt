package com.sshborg.data.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The server's pre-login banner, on its way to the password prompt.
 *
 * It arrives as whatever bytes the server chose, and it ends up in a box above the field where
 * the password is typed, so the line endings matter: a banner written on a Unix server travels
 * with CR LF all the same, and left as it comes it reads as double-spaced on screen.
 */
class BannerTextTest {

    @Test fun nothingArrivedMeansNothingToShow() {
        assertNull(SshManager.appendBanner(null, null))
        assertNull(SshManager.appendBanner(null, "   \r\n  \r\n"))
        assertEquals("kept", SshManager.appendBanner("kept", null))
        assertEquals("kept", SshManager.appendBanner("kept", "\r\n"))
    }

    @Test fun theWireEndingsBecomeLines() {
        assertEquals(
            "Authorized use only.\nAll activity is logged.",
            SshManager.appendBanner(null, "Authorized use only.\r\nAll activity is logged.\r\n"),
        )
    }

    @Test fun oneServerMaySendMoreThanOneBanner() {
        val first = SshManager.appendBanner(null, "Welcome.\r\n")
        assertEquals("Welcome.\nSecond thoughts.", SshManager.appendBanner(first, "Second thoughts.\r\n"))
    }

    /** A banner is laid out on purpose: the blank line between its paragraphs is part of it. */
    @Test fun blankLinesInsideStayAndOnlyTheTrailingOnesGo() {
        assertEquals(
            "  ===\n\n  A title\n\n  ===",
            SshManager.appendBanner(null, "  ===\r\n\r\n  A title\r\n\r\n  ===\r\n\r\n"),
        )
    }
}
