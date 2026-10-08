package com.sshborg.ui.sftp

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

/**
 * Looking a finished download up again in the media store, by the name and the folder it was
 * written under.
 *
 * Its own file because this is the one question the whole conflict machinery rests on — "is
 * something of that name already there?" — and the answer decides whether a file is overwritten,
 * renamed or skipped. It also needs a real device to check, so it is separated from the part that
 * only decides (see [DownloadConflicts]) and has an instrumented test of its own.
 */
object DownloadStore {

    /**
     * The row for [filename] in **exactly** [localDir], or null.
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
    fun find(resolver: ContentResolver, filename: String, localDir: String): Uri? {
        val folder = if (localDir.endsWith('/')) localDir else "$localDir/"
        return resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?",
            arrayOf(filename, folder),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
            ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
        }
    }
}
