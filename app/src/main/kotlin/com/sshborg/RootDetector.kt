package com.sshborg

import android.os.Build
import java.io.File

object RootDetector {

    fun isRooted(): Boolean {
        // Common su binary locations
        val suPaths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/magisk/.core/bin/su",
        )
        if (suPaths.any { File(it).exists() }) return true

        // Test-keys = custom ROM or unlocked bootloader, common on rooted devices
        if (Build.TAGS?.contains("test-keys") == true) return true

        return false
    }
}
