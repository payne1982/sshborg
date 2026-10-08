package com.sshborg.ui.sftp

import com.sshborg.service.TransferTask

/**
 * The decisions a download makes before a single byte moves: what to call a file when something of
 * that name is already in the folder, and what a batch does about the ones that collide.
 *
 * Gathered here, with no Android in it, for two reasons. It is the part that cost the most to get
 * right — the media store answers a colliding insert with a name of *its* choosing, silently, so
 * the app has to pick a free name or clear the old row first — and it was the part no test could
 * reach while it lived inside a view model holding a ContentResolver. The caller still owns every
 * question about what is actually on the device; this only decides what to do with the answers.
 */
object DownloadConflicts {

    /**
     * Splits [name] where a counter goes: everything up to the last dot, and the extension from
     * that dot on. A name whose only dot starts it has no extension — the dot belongs to the name,
     * so `.bashrc` numbers as `.bashrc(1)`, and `(1).bashrc` would have been a different file.
     */
    fun split(name: String): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
    }

    /** `file.txt` and 1 make `file(1).txt`. */
    fun numbered(name: String, counter: Int): String {
        val (base, ext) = split(name)
        return "$base($counter)$ext"
    }

    /**
     * The first of `name(1)`, `name(2)`, … that [taken] does not claim.
     *
     * [name] itself is never returned: this is only ever asked once the plain name is known to be
     * occupied, and handing it back would be the overwrite the user declined. Gaps are used, so a
     * folder holding `file(1).txt` and `file(3).txt` yields `file(2).txt` rather than `file(4).txt`.
     */
    fun firstFreeName(name: String, taken: (String) -> Boolean): String =
        generateSequence(1) { it + 1 }.map { numbered(name, it) }.first { !taken(it) }

    /** What a batch does once the user has answered: rows to clear out, and then files to fetch. */
    data class Plan(val toDelete: List<TransferTask>, val toDownload: List<TransferTask>)

    /**
     * [all] in the order it arrived, narrowed by [decision]; [conflicting] is the subset the caller
     * found already on the device.
     *
     * Overwriting is delete-then-write, never a write over the top: an insert onto an occupied name
     * does not replace it, it creates a second file under a name the store invents. So the rows to
     * remove are named separately, and removing them is the caller's first job.
     */
    fun plan(
        all: List<TransferTask>,
        conflicting: List<TransferTask>,
        decision: SftpViewModel.BatchConflictDecision,
    ): Plan = when (decision) {
        SftpViewModel.BatchConflictDecision.CANCEL ->
            Plan(emptyList(), emptyList())
        SftpViewModel.BatchConflictDecision.SKIP_EXISTING ->
            Plan(emptyList(), all.filter { it !in conflicting })
        SftpViewModel.BatchConflictDecision.OVERWRITE_ALL ->
            Plan(conflicting, all)
    }
}
