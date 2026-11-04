package com.sshborg.data

/**
 * How long a mosh session outlives the network it was born on.
 *
 * There are two clocks and they belong to different machines, which is the whole reason this is one
 * setting and not two:
 *
 * - the **server** is told `MOSH_SERVER_NETWORK_TMOUT`, and that is the real figure: it is what
 *   keeps the shell, and everything running in it, alive on somebody's host after the phone has
 *   stopped answering. Raising it is the part with a cost, which is why it is the part the person
 *   chooses;
 * - the **client** gives up on its own a few minutes earlier. It has to be earlier: waiting longer
 *   than the server means telling somebody "still trying" about a shell that has already exited,
 *   and then reconnecting them to nothing. [CLIENT_MARGIN_MINUTES] is that gap — enough to cover
 *   the two timers not having started at exactly the same moment and the round trip a resumed
 *   session still needs.
 *
 * The floor is an hour because that was the figure before the setting existed, and because anything
 * shorter is the behaviour mosh is there to prevent: a phone in a lift for ten minutes should find
 * its session where it left it.
 */
object MoshPatience {

    /** Both the default and the shortest allowed: see the class note. */
    const val DEFAULT_MINUTES = 60

    /** A day. Past this the question is no longer "did I lose the network". */
    const val MAX_MINUTES = 24 * 60

    /** How far ahead of the server the client gives up. */
    const val CLIENT_MARGIN_MINUTES = 5

    /** The choices offered in Settings, in minutes. */
    val CHOICES = listOf(60, 120, 240, 480, 720, 1440)

    /** What the server is told, whatever a stored or hand-edited value says. */
    fun serverMinutes(stored: Int): Int = stored.coerceIn(DEFAULT_MINUTES, MAX_MINUTES)

    /** What the server is told, in the seconds `MOSH_SERVER_NETWORK_TMOUT` wants. */
    fun serverSeconds(stored: Int): Int = serverMinutes(stored) * 60

    /**
     * How long the client keeps trying: the server's figure less [CLIENT_MARGIN_MINUTES].
     *
     * It cannot come out short or negative, because the floor on the server's side is twelve times
     * the margin — but it is derived here, in one place, so that moving either number can only move
     * both together.
     */
    fun clientMillis(stored: Int): Long =
        (serverMinutes(stored) - CLIENT_MARGIN_MINUTES) * 60 * 1000L
}
