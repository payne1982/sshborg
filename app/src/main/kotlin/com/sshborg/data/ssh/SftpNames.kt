package com.sshborg.data.ssh

/**
 * The one rule about names that come from the other end of an SFTP connection.
 *
 * A listing is the server's answer to "what is in this directory", and every name in that answer
 * is used as a name: appended to a remote path, shown in the browser, and — on the way back —
 * given to the filesystem on the phone as the file to write and, for a recursive download, as the
 * folder to make. Nothing downstream of the listing asks whether it really is one.
 *
 * No Unix filesystem can hold an entry called `..`, or one with a `/` inside it: the characters
 * are not available to a directory entry, which is precisely why a path arriving as a name means
 * something has gone wrong or is being tried on. So refusing it costs nothing a real server can
 * offer, and the alternative is a name deciding where the download lands.
 *
 * It lives in its own file because it is a trust boundary rather than a detail of listing, and
 * because a boundary with no test is a boundary nobody checked: a hostile server is the one thing
 * that cannot be arranged by hand on the phone.
 */
object SftpNames {

    /** Whether [name] is a single name, and therefore usable as one. */
    fun isPlain(name: String): Boolean =
        name.isNotEmpty() &&
            name != "." && name != ".." &&
            !name.contains('/') &&
            !name.contains('\u0000')
}
