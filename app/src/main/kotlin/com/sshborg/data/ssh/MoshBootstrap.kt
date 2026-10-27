package com.sshborg.data.ssh

/**
 * What `mosh-server` hands back when it starts: where to talk UDP, and the key for the session.
 *
 * SSH is only the ignition. It logs in, starts the server, reads one line, and that line is the
 * whole handover — after it the terminal rides UDP. The SSH session itself is **not** closed,
 * which is where we part company with the stock client: it carries SFTP, port forwarding, the
 * agent and the shell history, and all of that keeps working because it stays.
 */
class MoshBootstrap(val host: String, val port: Int, val key: String) {

    /** Deliberately says nothing: the key is the session's secret and must not reach a log. */
    override fun toString(): String = "MoshBootstrap($host:$port)"

    companion object {
        /**
         * The port range a host asks for, as `60001-60999` or a single port, turned into what
         * `mosh-server` wants after `-p`. Null for anything we cannot make sense of, and then the
         * server is left to its own default rather than handed nonsense.
         *
         * Deliberately forgiving about the separator, because this is typed by hand from a
         * firewall rule and a person may well write a colon the way mosh does.
         */
        fun portRange(asked: String?): String? {
            val text = asked?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val parts = text.split('-', ':').map { it.trim() }
            if (parts.size > 2) return null
            val from = parts[0].toIntOrNull() ?: return null
            val to = if (parts.size == 1) from else parts[1].toIntOrNull() ?: return null
            if (from !in 1..65535 || to !in 1..65535 || to < from) return null
            return if (from == to) "$from" else "$from:$to"
        }

        /**
         * What a host asked us to run, when it is a command and not a line of shell.
         *
         * The field exists for an account where `mosh-server` is somewhere the login shell's
         * PATH does not reach, so what belongs in it is a path or a name — and it is interpolated
         * into `env … $command new …`, which a remote shell then reads. A value carrying `;`, a
         * substitution or a redirect would therefore run as whatever it says, on the server, with
         * the account that just logged in.
         *
         * Typed by hand into the host's own form, that is nobody's problem but the typist's. It
         * matters because a host row also arrives by *import*: a backup file is not something a
         * person reads before applying it, and a field that is quietly a shell is not what anyone
         * importing a list of servers is agreeing to. So the shape is checked where the value is
         * used rather than where it was typed, and anything else falls back to the plain default
         * — the same answer [portRange] gives to a port range it cannot make sense of.
         *
         * An absolute path, a bare name, `~/bin/mosh-server`: all fine. A space is not, which
         * also means no arguments — if those are ever wanted they want a field of their own.
         */
        fun serverCommand(asked: String?): String? {
            val text = asked?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return text.takeIf { PLAIN_COMMAND.matches(it) }
        }

        private val PLAIN_COMMAND = Regex("[A-Za-z0-9._\\-/~+]+")

        /** `MOSH CONNECT <port> <key>`, the whole of the handover. */
        val CONNECT_LINE = Regex("MOSH CONNECT (\\d{1,5}) (\\S+)")

        /** Enough of what the server said to diagnose it, not enough to fill a dialog. */
        const val DETAIL_LIMIT = 2000

        /**
         * What may be shown to the user, or copied out of the error dialog, when the server said
         * something we could not use.
         *
         * The handover line goes first, before anything else happens to the text, because it holds
         * the session key. The one thing that must never be true is that a diagnostic somebody
         * pastes into an issue carries it — and a failure that still managed to print a handover
         * line is exactly the confusing case where somebody would paste the whole thing.
         */
        fun redact(said: String): String = said
            .replace(CONNECT_LINE, "MOSH CONNECT <port> <key withheld>")
            .trim()
            .take(DETAIL_LIMIT)
    }
}

/**
 * Why a mosh session could not be started.
 *
 * Each case is separate because each has a different cure, and the user is told which: the switch
 * is per-host and explicit, so somebody who asked for mosh and did not get it is owed the reason
 * rather than a silent fall back to SSH.
 */
class MoshUnavailable(val reason: Reason, val detail: String? = null) : Exception(reason.name) {

    enum class Reason {
        /** No `mosh-server` on the host. Cure: install it, or turn the switch off. */
        NotInstalled,

        /**
         * `mosh-server` refuses to run unless the server's locale is UTF-8, because the whole
         * protocol synchronises a terminal whose contents it has to be able to interpret. Cure is
         * on the server and nobody guesses it, which is why it is its own case.
         */
        LocaleNotUtf8,

        /** It ran and said something we could not read. The detail carries what it said. */
        NoConnectLine,

        /**
         * It started, but no datagram ever came back. Almost always inbound UDP 60001-60999
         * blocked by a firewall — there is no error from the far end for this, only silence.
         */
        NoUdpAnswer,
    }
}
