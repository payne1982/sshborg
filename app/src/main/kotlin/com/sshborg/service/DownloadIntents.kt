package com.sshborg.service

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri

/**
 * Builds the intent that opens a completed download, with one shared rule used everywhere
 * (completion notification, foreground download screen, background transfers panel):
 *
 *  - a normal file  -> ACTION_VIEW on its MediaStore content:// uri (no FileProvider needed);
 *  - an APK, or no file uri (a multi-file batch) -> the system Downloads screen, i.e. the file
 *    manager. APKs can't be opened directly without REQUEST_INSTALL_PACKAGES (intentionally
 *    absent), so we drop the user into Downloads to handle it themselves, same as a folder.
 */
object DownloadIntents {

    const val APK_MIME = "application/vnd.android.package-archive"

    fun open(uri: Uri?, mime: String?, filename: String? = null): Intent {
        val isApk = mime == APK_MIME || filename?.endsWith(".apk", ignoreCase = true) == true
        return if (uri != null && !isApk) {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        }
    }
}
