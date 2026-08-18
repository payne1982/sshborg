package com.sshborg

import android.app.Application
import com.jcraft.jsch.JSch
import com.sshborg.data.AppLockManager
import com.sshborg.data.AppPreferences
import com.sshborg.data.ssh.SshDiagnostics
import com.sshborg.data.db.AppDatabase
import com.sshborg.service.SessionManager
import com.sshborg.service.TransferManager
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class SshBorgApp : Application() {

    val db by lazy { AppDatabase.getInstance(this) }
    val sessionManager = SessionManager()
    val transferManager by lazy { TransferManager(this) }
    val appPreferences by lazy { AppPreferences(this) }
    val appLockManager by lazy { AppLockManager(appPreferences) }

    /** Timestamp of the last successful biometric authentication (in-memory only). */
    var lastAuthTime: Long = 0L

    override fun onCreate() {
        super.onCreate()
        // Android ships an old BouncyCastle without Ed25519/ECDSA-P521 support.
        // Remove it and register the current version so JSch key generation works
        // on all API levels (Ed25519 is only in Android's JCE from API 33+).
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())

        // Capture JSch's own diagnostics: an in-memory ring buffer (always) so a dropped
        // connection can show a real cause, plus Logcat output in debug builds only.
        JSch.setLogger(SshDiagnostics)
    }
}
