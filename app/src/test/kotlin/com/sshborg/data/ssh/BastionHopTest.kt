package com.sshborg.data.ssh

import com.sshborg.data.ssh.BastionHop.Endpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The line typed into a bastion's terminal to reach what is behind it.
 *
 * Two things are being guarded here. The shape — because the command is walked by somebody
 * else's ssh client and a wrong flag is a failure on a machine we cannot see — and the fact
 * that it is typed into a *shell*, which is the part that would otherwise go unnoticed until
 * the day a host name holds a semicolon.
 */
class BastionHopTest {

    @Test fun `the bastion alone needs no jump at all`() {
        assertEquals(
            "ssh alice@target.example.com",
            BastionHop.compose(Endpoint("alice", "target.example.com", 22)),
        )
    }

    @Test fun `a port that is not the default is carried as a flag`() {
        assertEquals(
            "ssh -p 2222 alice@target.example.com",
            BastionHop.compose(Endpoint("alice", "target.example.com", 2222)),
        )
    }

    @Test fun `the hops after the bastion are handed to ssh as one chain`() {
        assertEquals(
            "ssh -J bob@mid.example.com,carol@inner.example.com:2022 -p 2222 alice@target",
            BastionHop.compose(
                target  = Endpoint("alice", "target", 2222),
                through = listOf(
                    Endpoint("bob", "mid.example.com", 22),
                    Endpoint("carol", "inner.example.com", 2022),
                ),
            ),
        )
    }

    @Test fun `an address with colons in it gets the brackets ssh asks for`() {
        assertEquals(
            "ssh -J bob@[2001:db8::1]:2022 alice@2001:db8::2",
            BastionHop.compose(
                target  = Endpoint("alice", "2001:db8::2", 22),
                through = listOf(Endpoint("bob", "2001:db8::1", 2022)),
            ),
        )
    }

    @Test fun `brackets already there are left alone`() {
        assertEquals(
            "ssh -J bob@[2001:db8::1] alice@host",
            BastionHop.compose(
                target  = Endpoint("alice", "host", 22),
                through = listOf(Endpoint("bob", "[2001:db8::1]", 22)),
            ),
        )
    }

    /**
     * The one that matters. Nothing composed here is quoted, so anything a shell would read as
     * more than a name means no line at all — the caller then connects the ordinary way rather
     * than typing half a command on somebody's bastion.
     */
    @Test fun `a name a shell would read as something else composes nothing`() {
        val bad = listOf(
            "host; rm -rf /",
            "host\$(id)",
            "host`id`",
            "host name",
            "host'quote",
            "host\"quote",
            "host|pipe",
            "host&background",
            "host\nnewline",
            "host>redirect",
            "host\\escape",
            "",
        )
        for (name in bad) {
            assertNull("host name was accepted: <$name>", BastionHop.compose(Endpoint("alice", name, 22)))
            assertNull("user name was accepted: <$name>", BastionHop.compose(Endpoint(name, "host", 22)))
        }
    }

    @Test fun `a name ssh would read as an option composes nothing`() {
        // The character whitelist does not close this one by itself: `-oSomething` is made
        // entirely of characters a host name may legally contain, and a token beginning with a
        // dash is an option to the client rather than a machine to reach.
        for (name in listOf("-oProxyCommand", "-F", "-l", "--", "-")) {
            assertNull("host name was accepted: <$name>", BastionHop.compose(Endpoint("alice", name, 22)))
            assertNull("user name was accepted: <$name>", BastionHop.compose(Endpoint(name, "host", 22)))
            assertNull(
                "hop was accepted: <$name>",
                BastionHop.compose(Endpoint("alice", "host", 22), listOf(Endpoint("bob", name, 22))),
            )
        }
        // And a dash anywhere else is an ordinary character in an ordinary name.
        assertNotNull(BastionHop.compose(Endpoint("alice", "web-01.example.com", 22)))
    }

    @Test fun `an unusable hop is as fatal as an unusable target`() {
        assertNull(
            BastionHop.compose(
                target  = Endpoint("alice", "target", 22),
                through = listOf(Endpoint("bob", "mid; id", 22)),
            ),
        )
    }

    @Test fun `a port outside the range is refused`() {
        assertNull(BastionHop.compose(Endpoint("alice", "target", 0)))
        assertNull(BastionHop.compose(Endpoint("alice", "target", 65536)))
        assertNull(BastionHop.compose(Endpoint("alice", "target", -1)))
    }
}
