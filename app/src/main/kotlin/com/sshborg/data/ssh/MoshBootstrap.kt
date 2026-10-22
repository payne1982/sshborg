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
