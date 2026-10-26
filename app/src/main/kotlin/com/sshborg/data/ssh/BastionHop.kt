package com.sshborg.data.ssh

/**
 * The one `ssh` line typed into a bastion's own terminal to reach the host behind it.
 *
 * It exists because mosh cannot be tunnelled. A jump hop is an SSH channel inside the hop's own
 * connection, and mosh has no channels at all: it carries one terminal's screen state over UDP
 * and nothing else. So when a host's first hop is itself a mosh host, the session is made *to
 * the bastion* over mosh and the rest of the way is walked by the bastion's own ssh client.
 *
 * That is not a consolation prize. The terminal then lives on a machine whose network does not
 * change, which is the arrangement mosh's own documentation recommends over any attempt to jump:
 * the phone's walk from Wi-Fi to a carrier touches only the hop mosh protects, and the inward
 * `ssh` is server to server and never notices.
 *
 * The rest of the chain is handed over as `-J` rather than typed one hop at a time. `ssh h1;
 * ssh h2` does not nest — `;` runs the second one back on the bastion once the first has exited
 * — and writing the lines ahead of one another is a race against every prompt the hops raise,
 * where a host-key question or a password prompt eats the line that was already in flight. One
 * command, walked by a client that knows how to wait for its own prompts.
 */
object BastionHop {

    /** A machine on the composed command line. */
    data class Endpoint(val user: String, val host: String, val port: Int)

    /**
     * What may appear in a name that we put on somebody else's shell command line.
     *
     * The line is typed into an interactive shell on the bastion, so a name carrying a space, a
     * quote or a `$` would stop being one argument. Everything a host name or a user name can
     * legally hold is in here, brackets and `%` included for an IPv6 literal with a scope id;
     * anything else means the line is not composed at all.
     *
     * Quoting every name would have worked too, and it was the first thing tried. It was dropped
     * because this line is read by the person it is typed in front of — and edited by them, which
     * is the other thing they do with it — and `ssh '…'@'…'` is not a line anyone wants to meet.
     */
    private val SAFE = Regex("""[A-Za-z0-9._\-:\[\]%]+""")

    /** The port that needs no `-p` and no `:suffix`. */
    private const val DEFAULT_PORT = 22

    /**
     * The command, or null when a name on it would not survive the shell it is typed into.
     *
     * @param target the host actually being reached — the row the person tapped.
     * @param through the hops after the bastion, in order; empty when the bastion is the only one.
     */
    fun compose(target: Endpoint, through: List<Endpoint> = emptyList()): String? {
        if (!isSafe(target)) return null
        if (through.any { !isSafe(it) }) return null
        return buildString {
            append("ssh")
            if (through.isNotEmpty()) {
                append(" -J ")
                append(through.joinToString(",") { hop ->
                    val port = if (hop.port != DEFAULT_PORT) ":${hop.port}" else ""
                    "${hop.user}@${bracketed(hop.host)}$port"
                })
            }
            if (target.port != DEFAULT_PORT) append(" -p ${target.port}")
            append(" ${target.user}@${target.host}")
        }
    }

    /**
     * An IPv6 literal inside `-J` needs its brackets: `user@2001:db8::1:2222` has no reading, and
     * ssh's own answer to that is `user@[2001:db8::1]:2222`. The final target never takes a port
     * suffix — it gets `-p` — so it is left exactly as it was typed.
     */
    private fun bracketed(host: String): String =
        if (host.contains(':') && !host.startsWith("[")) "[$host]" else host

    private fun isSafe(e: Endpoint): Boolean =
        e.user.isNotBlank() && e.host.isNotBlank() &&
            SAFE.matches(e.user) && SAFE.matches(e.host) && e.port in 1..65535
}
