package com.sshborg.data.ssh

import org.junit.Assert.assertEquals
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
}
