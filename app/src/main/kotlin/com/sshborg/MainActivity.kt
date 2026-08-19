package com.sshborg

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sshborg.data.AppLockManager
import com.sshborg.data.AppPreferences
import com.sshborg.ui.lock.AppLockScreen
import com.sshborg.ui.theme.SshBorgTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var privacyOverlay: View? = null
    private var isAuthenticating = false

    // In-app lock (LOCK_SECRET): the Compose lock gate is drawn over the app content.
    private val showAppLock = mutableStateOf(false)
    private val lockKind = mutableStateOf(AppLockManager.Kind.PIN)
    private val initialLockoutSeconds = mutableStateOf(0L)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val prefs = (application as SshBorgApp).appPreferences
        setContent {
            val nightMode by prefs.nightMode.collectAsState(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            SshBorgTheme(nightMode = nightMode) {
                Box(Modifier.fillMaxSize()) {
                    AppNavigation()
                    // Opaque lock gate on top of (and preserving) the live app content.
                    if (showAppLock.value) {
                        val app = application as SshBorgApp
                        AppLockScreen(
                            kind = lockKind.value,
                            verify = { app.appLockManager.verify(it) },
                            onUnlocked = {
                                app.lastAuthTime = System.currentTimeMillis()
                                showAppLock.value = false
                                setPrivacy(false)
                            },
                            initialLockoutSeconds = initialLockoutSeconds.value,
                        )
                    }
                }
            }
        }
        // Plain View on top of Compose — visibility controlled directly, no recomposition involved
        val tv = TypedValue()
        theme.resolveAttribute(android.R.attr.colorBackground, tv, true)
        val overlay = View(this).apply { setBackgroundColor(tv.data) }
        window.addContentView(overlay, android.view.ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        privacyOverlay = overlay
    }

    private fun setPrivacy(locked: Boolean) {
        privacyOverlay?.visibility = if (locked) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        if (!BuildConfig.DEBUG) {
            lifecycleScope.launch {
                val allow = (application as SshBorgApp).appPreferences.allowScreenshots.first()
                if (allow) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Cover content when the app is actually stopped (backgrounded, recents, another
        // full-screen app, or the device-credential auth Activity) so it isn't shown on
        // return until onStart clears it. Deliberately NOT in onPause: a mere pause that
        // never reaches onStop — e.g. Gboard's voice-input panel or a transient dialog —
        // is not followed by onStart, so an onPause cover would get stuck grey until the
        // user fully backgrounds and reopens the app. onStop <-> onStart is symmetric.
        // The recents thumbnail is protected independently by FLAG_SECURE (see onResume).
        setPrivacy(true)
    }

    override fun onStart() {
        super.onStart()
        // Guard against re-entry: DEVICE_CREDENTIAL auth starts a new Activity which causes
        // onStop/onStart to fire while the original authenticate() call is still suspended.
        if (isAuthenticating) return
        val app = application as SshBorgApp
        lifecycleScope.launch {
            val mode = app.appPreferences.lockMode.first()
            if (mode == AppPreferences.LOCK_NONE) {
                showAppLock.value = false
                setPrivacy(false)
                return@launch
            }
            val timeoutMs = app.appPreferences.lockTimeoutSeconds.first() * 1_000L
            val elapsed = System.currentTimeMillis() - app.lastAuthTime
            if (elapsed <= timeoutMs) {
                showAppLock.value = false
                setPrivacy(false)
                return@launch
            }
            if (mode == AppPreferences.LOCK_SECRET) {
                val kind = app.appLockManager.kind()
                if (kind == null) {
                    // Mode selected but no secret stored — nothing to check, don't lock out.
                    showAppLock.value = false
                    setPrivacy(false)
                    return@launch
                }
                lockKind.value = kind
                initialLockoutSeconds.value = app.appLockManager.lockoutRemainingSeconds()
                showAppLock.value = true
                // Keep the plain-View cover until the Compose gate has drawn, so no content flashes.
                window.decorView.post { setPrivacy(false) }
                return@launch
            }
            isAuthenticating = true
            val ok = BiometricHelper.authenticate(
                this@MainActivity,
                allowDeviceCredential = mode == AppPreferences.LOCK_DEVICE,
            )
            isAuthenticating = false
            if (ok) {
                app.lastAuthTime = System.currentTimeMillis()
                setPrivacy(false)
            } else {
                finish()
            }
        }
    }
}
