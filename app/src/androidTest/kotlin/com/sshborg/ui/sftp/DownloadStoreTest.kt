package com.sshborg.ui.sftp

import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Whether "is a file of this name already in that folder?" answers about **that** folder and no
 * other.
 *
 * Instrumented, because the question is a SQL selection against the media store and the thing that
 * was wrong was the SQL: the folder used to be matched as a fragment, so a row one level down
 * answered for the folder above it — and overwriting then deleted that row, a file the user never
 * named. A JVM test cannot see any of this.
 *
 * It works in folders of its own, so a run leaves nothing in anybody's downloads. The pair it uses
 * stands in the same relation as the app's release and debug folders — one name is a prefix of the
 * other — which is precisely the pair a fragment match could not tell apart.
 */
@RunWith(AndroidJUnit4::class)
class DownloadStoreTest {

    private val resolver =
        InstrumentationRegistry.getInstrumentation().targetContext.contentResolver

    private val folder       = "${Environment.DIRECTORY_DOWNLOADS}/sshborg-query-test/"
    private val below        = "${folder}sub/"
    private val prefixSibling = "${Environment.DIRECTORY_DOWNLOADS}/sshborg-query-test-debug/"
    private val underscored  = "${Environment.DIRECTORY_DOWNLOADS}/sshborg-query-test/a_b/"

    private val created = mutableListOf<Uri>()

    /** Writes a one-byte file called [name] into [dir] and returns the row it made. */
    private fun put(name: String, dir: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, dir)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("the store refused a row in $dir")
        created += uri
        resolver.openOutputStream(uri)!!.use { it.write(0) }
        return uri
    }

    private fun nameOf(uri: Uri): String = resolver.query(
        uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null,
    )!!.use { it.moveToFirst(); it.getString(0) }

    private fun find(name: String, dir: String) = DownloadStore.find(resolver, name, dir)

    @After fun removeWhatThisRunWrote() {
        created.forEach { runCatching { resolver.delete(it, null, null) } }
        created.clear()
    }

    @Test fun aFileIsFoundInTheFolderItWasWrittenTo() {
        val uri = put("report.txt", folder)
        assertEquals(uri, find("report.txt", folder))
    }

    @Test fun aFileOneLevelDownDoesNotAnswerForTheFolderAboveIt() {
        // The bug this test exists for: a batch download of a folder writes into <folder>/sub/,
        // and a later download of the same name into <folder> was called a collision — with
        // "overwrite" then deleting the file in sub/.
        put("report.txt", below)
        assertNull(find("report.txt", folder))
    }

    @Test fun theFolderBelowIsFoundWhenItIsTheOneAskedAbout() {
        val uri = put("report.txt", below)
        assertEquals(uri, find("report.txt", below))
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
        assertNull(find("report.txt", "${Environment.DIRECTORY_DOWNLOADS}/sshborg-query-test/axb/"))
        assertEquals(1, created.size)
    }

    @Test fun aNameThatIsNotThereIsNotFound() {
        put("report.txt", folder)
        assertNull(find("nothing-of-this-name.txt", folder))
    }

    @Test fun theTrailingSeparatorIsOptionalForTheCaller() {
        val uri = put("report.txt", folder)
        assertEquals(uri, find("report.txt", folder.trimEnd('/')))
    }

    @Test fun theStoreInventsANameForACollidingInsertWhichIsWhyAFreeOneIsChosenFirst() {
        // The premise the whole conflict design rests on, and the only place it can be checked:
        // an insert onto an occupied name is not refused and does not replace. If Android ever
        // changes this, the app's "keep both" and "overwrite" both stop making sense — and this
        // test is how we find out.
        put("report.txt", folder)
        val second = put("report.txt", folder)
        assertNotEquals("report.txt", nameOf(second))
    }
}
