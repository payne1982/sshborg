package com.sshborg.ui.sftp

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream

/**
 * Where a download goes, and how to ask whether something of that name is already there.
 *
 * The one place in the app that knows about either answer, because there are two. From Android 10
 * a download is a row in the media store, which the app may write without any permission and which
 * survives the app being uninstalled. Before that there is no such collection: a file in the public
 * Downloads folder is a file, reached through the filesystem and needing a permission the user has
 * to grant.
 *
 * Everything else in the download path speaks in [DownloadRef] and never learns which of the two it
 * got. The decisions about *what* to do — number a colliding name, overwrite, skip — are in
 * [DownloadConflicts] and are the same on both.
 */
sealed interface DownloadRef

/** A media-store row, from Android 10 on. */
data class StoreRow(val uri: Uri) : DownloadRef

/** A file in the public Downloads folder, on Android 9 and below. */
data class LocalFile(val file: File) : DownloadRef

object DownloadStore {

    /** True where there is no Downloads collection and the filesystem has to be used instead. */
    private val legacy get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    /**
     * True when a download cannot start until the user has granted storage access.
     *
     * Only ever true on Android 9 and below: writing into the public Downloads folder is a
     * filesystem write there, and the media store asks for nothing from Android 10 on.
     */
    val needsStoragePermission: Boolean get() = legacy

    /**
     * The download called [filename] in **exactly** [localDir], or null.
     *
     * The folder has to match whole, not as a fragment. A batch download of a folder writes into
     * `…/SSHBorg/<name>/`, so a pattern match on `…/SSHBorg` would answer for files sitting one
     * level down: a download into the top folder would be reported as a collision with a file that
     * is not in it, and overwriting would then delete that other file instead. A fragment match
     * also handed the folder name's own `_` and `%` to SQL as wildcards, and folder names come
     * from the server.
     *
     * The media store keeps `RELATIVE_PATH` with a trailing separator, so [localDir] is given one
     * if the caller left it off.
     */
    fun find(context: Context, filename: String, localDir: String): DownloadRef? {
        if (legacy) return File(legacyDir(localDir), filename).takeIf { it.isFile }?.let(::LocalFile)
        return context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?",
            arrayOf(filename, withSeparator(localDir)),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
            StoreRow(ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id))
        }
    }

    /**
     * Makes the place to write [filename] into, or null when the device refuses — a full disk, a
     * permission the user declined, a name the filesystem will not take.
     *
     * The caller must have settled any collision first. Neither backend replaces what is there:
     * the media store answers a colliding insert with a name of its own choosing, so the old row
     * has to be [remove]d, and the filesystem would truncate a file the user may have asked to
     * keep. [DownloadConflicts] is what decides which of those happens.
     */
    fun create(context: Context, filename: String, localDir: String, mime: String): DownloadRef? {
        if (legacy) {
            val dir = legacyDir(localDir)
            if (!dir.isDirectory && !dir.mkdirs()) return null
            return LocalFile(File(dir, filename))
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, withSeparator(localDir))
        }
        return context.contentResolver
            .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?.let(::StoreRow)
    }

    /** The stream the bytes go into. Truncates, so a retry over a part-written file is clean. */
    fun openStream(context: Context, ref: DownloadRef): OutputStream = when (ref) {
        is LocalFile -> ref.file.outputStream()
        is StoreRow  -> context.contentResolver.openOutputStream(ref.uri, "wt")
            ?: error("the store would not open ${ref.uri} for writing")
    }

    /** Takes the file away: the half of an overwrite that has to happen before the write. */
    fun remove(context: Context, ref: DownloadRef) {
        when (ref) {
            is LocalFile -> ref.file.delete()
            is StoreRow  -> context.contentResolver.delete(ref.uri, null, null)
        }
    }

    /**
     * A uri another app may open, for the tap that views a finished download.
     *
     * A media-store row already is one. A file is not: handing out `file://` has thrown
     * `FileUriExposedException` since Android 7, so it goes through the app's own provider, and the
     * intent carries the read grant (see DownloadIntents).
     */
    fun viewUri(context: Context, ref: DownloadRef): Uri = when (ref) {
        is StoreRow  -> ref.uri
        is LocalFile -> FileProvider.getUriForFile(
            context, "${context.packageName}.downloads", ref.file,
        )
    }

    private fun withSeparator(localDir: String) =
        if (localDir.endsWith('/')) localDir else "$localDir/"

    /**
     * [localDir] resolved against external storage. It already starts with `Download/`, which is
     * why this hangs it off the storage root rather than off the Downloads folder itself.
     */
    @Suppress("DEPRECATION")   // the only correct call below Android 10, where it is not deprecated
    private fun legacyDir(localDir: String) =
        File(Environment.getExternalStorageDirectory(), localDir)
}
