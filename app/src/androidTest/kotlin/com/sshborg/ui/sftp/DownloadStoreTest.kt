package com.sshborg.ui.sftp

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Whether "is a file of this name already in that folder?" answers about **that** folder and no
 * other — and it has to hold on both of the places a download can live: a media-store row from
 * Android 10, a file in the public Downloads folder before that.
 *
 * Instrumented, because on a modern device the question is a SQL selection against the store and
 * the thing that was wrong was the SQL: the folder used to be matched as a fragment, so a row one
 * level down answered for the folder above it — and overwriting then deleted that row, a file the
 * user never named. A JVM test cannot see any of this.
 *
 * Everything here goes through [DownloadStore] and never names a backend, so the same run on an
 * API 28 emulator checks the filesystem path instead. The one test that is about the store's own
 * behaviour says so and stands aside below Android 10.
 *
 * It works in folders of its own, so a run leaves nothing in anybody's downloads. The pair it uses
 * stands in the same relation as the app's release and debug folders — one name is a prefix of the
 * other — which is precisely the pair a fragment match could not tell apart.
 */
@RunWith(AndroidJUnit4::class)
class DownloadStoreTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    /**
     * A folder of this run's own. The media store does not always let go of a path when the row
     * that held it is deleted — a second run then meets `UNIQUE constraint failed: files._data`
     * from its own history — so no two runs are ever given the same name to collide over.
     */
    private val folder        = "${Environment.DIRECTORY_DOWNLOADS}/sshborg-test-${System.nanoTime()}/"
    private val below         = "${folder}sub/"
    /** Named so that [folder]'s name is a prefix of it, as the release and debug folders are. */
    private val prefixSibling = "${folder.trimEnd('/')}-debug/"
    private val underscored   = "${folder}a_b/"

    private val created = mutableListOf<DownloadRef>()

    @Before fun grantStorageWhereADownloadNeedsIt() {
        // Only Android 9 and below ask, and uiAutomation can grant from exactly that version.
        if (DownloadStore.needsStoragePermission) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName, Manifest.permission.WRITE_EXTERNAL_STORAGE,
            )
        }
    }

    @After fun removeWhatThisRunWrote() {
        created.forEach { runCatching { DownloadStore.remove(context, it) } }
        created.clear()
    }

    /** Writes a one-byte file called [name] into [dir], the way a download does. */
    private fun put(name: String, dir: String): DownloadRef {
        val ref = DownloadStore.create(context, name, dir, "text/plain")
            ?: error("the device refused a file called $name in $dir")
        created += ref
        DownloadStore.openStream(context, ref).use { it.write(0) }
        return ref
    }

    private fun find(name: String, dir: String) = DownloadStore.find(context, name, dir)

    @Test fun aFileIsFoundInTheFolderItWasWrittenTo() {
        put("report.txt", folder)
        assertEquals(1, created.size)
        assertEquals(created.single(), find("report.txt", folder))
    }

    @Test fun aFileOneLevelDownDoesNotAnswerForTheFolderAboveIt() {
        // The bug this test exists for: a batch download of a folder writes into <folder>/sub/,
        // and a later download of the same name into <folder> was called a collision — with
        // "overwrite" then deleting the file in sub/.
        put("report.txt", below)
        assertNull(find("report.txt", folder))
    }

    @Test fun theFolderBelowIsFoundWhenItIsTheOneAskedAbout() {
        val ref = put("report.txt", below)
        assertEquals(ref, find("report.txt", below))
    }

    @Test fun aFolderWhoseNameStartsLikeAnotherIsADifferentFolder() {
        // The app's debug build downloads into SSHBorg-debug/ next to the release SSHBorg/. Under
        // a fragment match each one's query answered for the other's files.
        put("report.txt", prefixSibling)
        assertNull(find("report.txt", folder))
    }

    @Test fun anUnderscoreInAFolderNameIsACharacterAndNotAWildcard() {
        // Folder names come from the remote server, and SQL LIKE reads _ as "any character".
        put("report.txt", underscored)
        assertNull(find("report.txt", "${folder}axb/"))
    }

    @Test fun aNameThatIsNotThereIsNotFound() {
        put("report.txt", folder)
        assertNull(find("nothing-of-this-name.txt", folder))
    }

    @Test fun theTrailingSeparatorIsOptionalForTheCaller() {
        val ref = put("report.txt", folder)
        assertEquals(ref, find("report.txt", folder.trimEnd('/')))
    }

    @Test fun aFinishedDownloadCanBeHandedToAnotherApp() {
        // On Android 9 this is the provider's doing, and a file:// uri would have thrown instead.
        val uri = DownloadStore.viewUri(context, put("report.txt", folder))
        assertEquals("content", uri.scheme)
    }

    @Test fun theStoreInventsANameForACollidingInsertWhichIsWhyAFreeOneIsChosenFirst() {
        // The premise the conflict design rests on, and the only place it can be checked: an
        // insert onto an occupied name is not refused and does not replace. If Android ever
        // changes this, "keep both" and "overwrite" both stop making sense — and this test is how
        // we find out. Below Android 10 there is no insert and a write simply truncates, which is
        // why this one stands aside there.
        assumeTrue(
            "no media store below Android 10: a file write truncates and invents nothing",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
        )
        put("report.txt", folder)
        val second = put("report.txt", folder)
        assertNotEquals("report.txt", displayNameOf((second as StoreRow).uri))
    }

    private fun displayNameOf(uri: Uri): String = context.contentResolver.query(
        uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null,
    )!!.use { it.moveToFirst(); it.getString(0) }
}
