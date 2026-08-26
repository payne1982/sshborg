package com.sshborg

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
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

        // Debug-only: track the active network transport so a disconnect breadcrumb can show
        // whether Wi-Fi/cellular flipped around the drop (see SshDiagnostics). Never in release.
        if (BuildConfig.DEBUG) registerNetworkDiagnostics()
    }

    private fun registerNetworkDiagnostics() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) = SshDiagnostics.setNet("none")
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                SshDiagnostics.setNet(
                    when {
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)     -> "wifi"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)      -> "vpn"
                        else -> "other"
                    }
                )
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }
    }
}
