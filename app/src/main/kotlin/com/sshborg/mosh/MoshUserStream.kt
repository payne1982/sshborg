package com.sshborg.mosh

/**
 * What the user has done, as the state the client synchronises upward.
 *
 * Our side of the protocol is a growing list of actions, and a "state" is simply how many of them
 * had happened — so the diff between two states is the actions in between, and the state number
 * the server acknowledges tells us which ones it has. That is all the state the client needs; the
 * expensive half of mosh, keeping a terminal and computing screen diffs, is the server's job.
 *
 * Runs of typing are joined into one keystroke instruction on the way out. The server feeds the
 * bytes to the pty in order, so a hundred separate instructions and one instruction of a hundred
 * bytes mean the same thing, and the second is smaller.
 */
class MoshUserStream {

    private val actions = ArrayList<MoshDiffs.UserAction>()
    private var dropped = 0L

    /** How many actions have happened in all, including the ones already acknowledged and freed. */
    val count: Long get() = dropped + actions.size

    fun type(bytes: ByteArray) {
        if (bytes.isNotEmpty()) actions.add(MoshDiffs.UserAction.Keystrokes(bytes))
    }

    fun resize(width: Int, height: Int) {
        actions.add(MoshDiffs.UserAction.Resize(width, height))
    }

    /** The diff that takes the far end from [from] actions to everything we have. */
    fun diffFrom(from: Long): ByteArray {
        require(from >= dropped) { "actions before $dropped have been freed; cannot diff from $from" }
        require(from <= count) { "cannot diff from $from, only $count actions have happened" }
        if (from == count) return EMPTY
        return MoshDiffs.encodeUserDiff(coalesce(actions.subList((from - dropped).toInt(), actions.size)))
    }

    /** Frees everything the far end has acknowledged; the numbering carries on regardless. */
    fun dropThrough(upTo: Long) {
        val removable = (upTo - dropped).coerceIn(0, actions.size.toLong()).toInt()
        if (removable == 0) return
        actions.subList(0, removable).clear()
        dropped += removable
    }

    private fun coalesce(window: List<MoshDiffs.UserAction>): List<MoshDiffs.UserAction> {
        val out = ArrayList<MoshDiffs.UserAction>(window.size)
        var pending: MutableList<ByteArray>? = null
        fun flush() {
            pending?.let { runs ->
                val total = runs.sumOf { it.size }
                val joined = ByteArray(total)
                var at = 0
                for (run in runs) {
                    System.arraycopy(run, 0, joined, at, run.size)
                    at += run.size
                }
                out.add(MoshDiffs.UserAction.Keystrokes(joined))
            }
            pending = null
        }
        for (action in window) {
            when (action) {
                is MoshDiffs.UserAction.Keystrokes ->
                    (pending ?: ArrayList<ByteArray>().also { pending = it }).add(action.keys)
                is MoshDiffs.UserAction.Resize -> {
                    flush()
                    out.add(action)
                }
            }
        }
        flush()
        return out
    }

    private companion object {
        val EMPTY = ByteArray(0)
    }
}
