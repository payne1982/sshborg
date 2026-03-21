package com.sshborg

import android.app.Application
import com.sshborg.data.db.AppDatabase
import com.sshborg.service.SessionManager
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class SshBorgApp : Application() {

    val db by lazy { AppDatabase.getInstance(this) }
    val sessionManager = SessionManager()

    override fun onCreate() {
        super.onCreate()
        // Android ships an old BouncyCastle without Ed25519/ECDSA-P521 support.
        // Remove it and register the current version so JSch key generation works
        // on all API levels (Ed25519 is only in Android's JCE from API 33+).
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())
    }
}
