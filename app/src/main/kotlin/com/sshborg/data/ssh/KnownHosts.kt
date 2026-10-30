package com.sshborg.data.ssh

/**
 * The host key a saved host has pinned, as `known_hosts` lines, and the two questions asked of it
 * when a server offers a key.
 *
 * A server offering something other than what we pinned is not the same event as a server we have
 * never met, and the difference is the whole value of pinning a key at all: the first is a machine
 * being introduced, the second is the machine we trusted saying it is now a different one. Both
 * reach the same dialog, so the dialog has to be told which it is looking at.
 *
 * It is worked out here rather than read off JSch's warning text. JSch does distinguish the two —
 * its message for a changed key is the familiar wall of capitals — but a message is wording, and
 * wording belongs to the library and changes with it. The question is answerable from what we
 * stored and what arrived, so it is answered from those.
 */
object KnownHosts {

    /**
     * Whether [arriving] takes the place of a key already pinned for the same host *and the same
     * algorithm*.
     *
     * Same algorithm, because a host offering an ed25519 key where only an RSA one was pinned has
     * not changed its identity — it has an identity we have not seen yet, which is what `ssh`
     * itself treats it as. And same host, because a pinned line survives the hostname or the port
     * on its row being edited: a line that no longer names what we dialled is stale, and a stale
     * line is not evidence of anything.
     */
    fun identityChanged(pinned: String?, arriving: String): Boolean {
        val host = fieldOf(arriving, 0) ?: return false
        val type = fieldOf(arriving, 1) ?: return false
        val key = fieldOf(arriving, 2) ?: return false
        return lines(pinned).any { matches(it, host, type) && fieldOf(it, 2) != key }
    }

    /**
     * What to store once the person has accepted [arriving]: the line that was pinned for that
     * host and algorithm gives way to it, and everything else is kept.
     *
     * Kept, because the other lines are other algorithms and other hosts, and accepting one key
     * says nothing about them. This is also the whole of what "accept" used to fail to do — the
     * key was pinned only when nothing was pinned yet, so the one case where the dialog really
     * mattered was the one case where saying yes changed nothing and the next connection asked
     * again.
     */
    fun replacing(pinned: String?, arriving: String): String {
        val host = fieldOf(arriving, 0)
        val type = fieldOf(arriving, 1)
        val kept =
            if (host == null || type == null) lines(pinned)
            else lines(pinned).filterNot { matches(it, host, type) }
        return (kept + arriving).joinToString("\n")
    }

    /** A line is about this host and algorithm. The host field may be a comma-separated list. */
    private fun matches(line: String, host: String, type: String): Boolean =
        fieldOf(line, 1) == type &&
            fieldOf(line, 0)?.split(',')?.any { it == host } == true

    private fun lines(entry: String?): List<String> =
        entry?.lines()?.filter { it.isNotBlank() } ?: emptyList()

    private fun fieldOf(line: String, index: Int): String? =
        line.trim().split(' ', '\t').filter { it.isNotEmpty() }.getOrNull(index)
}
