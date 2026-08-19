package com.sshborg

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * True when running on a television. Used to steer the lock UI: biometric and
 * device-credential modes are usually unavailable on a TV, so they are hidden and
 * the in-app PIN/passphrase is offered instead.
 */
fun isTelevision(context: Context): Boolean {
    val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    if (uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
    return context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}
