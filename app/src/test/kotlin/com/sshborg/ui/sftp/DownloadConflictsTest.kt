package com.sshborg.ui.sftp

import com.sshborg.service.TransferTask
import com.sshborg.ui.sftp.SftpViewModel.BatchConflictDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a download does when the name is already taken — the part that took the longest to get
 * right on a device and had no test at all until this one.
 *
 * The two properties worth pinning are not visible from reading the code. A free name has to be
 * chosen *before* the file is created, because the media store will not refuse a colliding insert:
 * it invents `file (1).ext` of its own and the app would never learn which name the bytes landed
 * under. And overwriting has to be a delete followed by a write, for the same reason — which is
 * why [DownloadConflicts.plan] names the rows to remove instead of trusting the insert to replace.
 */
class DownloadConflictsTest {

    // ── The name a colliding download gets ────────────────────────────────────

    @Test fun `the counter goes before the extension, not after the name`() {
        assertEquals("file(1).txt", DownloadConflicts.numbered("file.txt", 1))
        assertEquals("file(7).txt", DownloadConflicts.numbered("file.txt", 7))
    }

    @Test fun `a name with no extension just gets the counter`() {
        assertEquals("README(1)", DownloadConflicts.numbered("README", 1))
    }

    @Test fun `a dotfile keeps its leading dot`() {
        // `(1).bashrc` would be a different file, and a hidden one in a different place
        // alphabetically. The leading dot is part of the name, not an extension.
        assertEquals(".bashrc" to "", DownloadConflicts.split(".bashrc"))
        assertEquals(".bashrc(1)", DownloadConflicts.numbered(".bashrc", 1))
    }

    @Test fun `only the last dot counts as the extension`() {
        // Known and deliberate: a double extension is not understood, so a tarball numbers as
        // archive.tar(1).gz. It stays openable and stays unique, which is what matters here;
        // teaching it about .tar.gz would mean a list of special cases.
        assertEquals("archive.tar(1).gz", DownloadConflicts.numbered("archive.tar.gz", 1))
    }

    @Test fun `a trailing dot is an empty extension and survives`() {
        assertEquals("file(1).", DownloadConflicts.numbered("file.", 1))
    }

    @Test fun `spaces and non-ascii are left alone`() {
        assertEquals("rapporto finale(1).pdf", DownloadConflicts.numbered("rapporto finale.pdf", 1))
        assertEquals("приложение(1).apk", DownloadConflicts.numbered("приложение.apk", 1))
    }

    @Test fun `the first free number is taken, counting from one`() {
        val taken = setOf("file(1).txt", "file(2).txt")
        assertEquals("file(3).txt", DownloadConflicts.firstFreeName("file.txt") { it in taken })
    }

    @Test fun `a gap in the numbering is filled, not skipped past`() {
        val taken = setOf("file(1).txt", "file(3).txt")
        assertEquals("file(2).txt", DownloadConflicts.firstFreeName("file.txt") { it in taken })
    }

    @Test fun `the plain name is never handed back, even when nothing is taken`() {
        // This is only ever asked after the plain name was found occupied: returning it would be
        // the overwrite the user just declined.
        assertEquals("file(1).txt", DownloadConflicts.firstFreeName("file.txt") { false })
    }

    @Test fun `the folder is asked about the candidates in order and nothing else`() {
        val asked = mutableListOf<String>()
        val name = DownloadConflicts.firstFreeName("file.txt") { candidate ->
            asked += candidate
            candidate != "file(3).txt"
        }
        assertEquals("file(3).txt", name)
        assertEquals(listOf("file(1).txt", "file(2).txt", "file(3).txt"), asked)
    }

    // ── What a batch does with the files that collide ─────────────────────────

    private fun task(name: String, dir: String = "Download/SSHBorg/") =
        TransferTask(remotePath = "/remote/$name", filename = name, localDir = dir)

    private val a = task("a.txt")
    private val b = task("b.txt")
    private val c = task("c.txt")
    private val all = listOf(a, b, c)
    private val colliding = listOf(a, c)

    @Test fun `cancel downloads nothing and removes nothing`() {
        val plan = DownloadConflicts.plan(all, colliding, BatchConflictDecision.CANCEL)
        assertEquals(emptyList<TransferTask>(), plan.toDownload)
        assertEquals(emptyList<TransferTask>(), plan.toDelete)
    }

    @Test fun `skip keeps the new files and leaves what is already there untouched`() {
        val plan = DownloadConflicts.plan(all, colliding, BatchConflictDecision.SKIP_EXISTING)
        assertEquals(listOf(b), plan.toDownload)
        assertEquals(emptyList<TransferTask>(), plan.toDelete)
    }

    @Test fun `overwrite fetches everything and removes exactly what it will replace`() {
        val plan = DownloadConflicts.plan(all, colliding, BatchConflictDecision.OVERWRITE_ALL)
        assertEquals(all, plan.toDownload)
        assertEquals(colliding, plan.toDelete)
    }

    @Test fun `overwrite never leaves a file to be replaced by the insert alone`() {
        // The invariant the device taught us: everything downloaded onto an occupied name must
        // appear in toDelete, or the store writes a second file under a name of its own.
        val plan = DownloadConflicts.plan(all, colliding, BatchConflictDecision.OVERWRITE_ALL)
        assertTrue(plan.toDownload.filter { it in colliding }.all { it in plan.toDelete })
    }

    @Test fun `order survives, because the batch reports progress by position`() {
        val plan = DownloadConflicts.plan(all, listOf(b), BatchConflictDecision.SKIP_EXISTING)
        assertEquals(listOf(a, c), plan.toDownload)
    }

    @Test fun `the same name in two folders is two files and only the taken one is skipped`() {
        val top    = task("x.txt", "Download/SSHBorg/")
        val nested = task("x.txt", "Download/SSHBorg/sub/")
        val plan = DownloadConflicts.plan(
            listOf(top, nested), listOf(nested), BatchConflictDecision.SKIP_EXISTING,
        )
        assertEquals(listOf(top), plan.toDownload)
    }

    @Test fun `nothing colliding leaves the batch whole, whatever was answered`() {
        for (decision in listOf(
            BatchConflictDecision.SKIP_EXISTING, BatchConflictDecision.OVERWRITE_ALL,
        )) {
            val plan = DownloadConflicts.plan(all, emptyList(), decision)
            assertEquals(all, plan.toDownload)
            assertEquals(emptyList<TransferTask>(), plan.toDelete)
        }
    }
}
