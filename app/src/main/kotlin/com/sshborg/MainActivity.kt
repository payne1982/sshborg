package com.sshborg

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
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
    // Whether a lock is actually configured. When it isn't, there's nothing to hide
    // before auth, so onStop must not cover content (it would just flash on every return).
    // Cached from onStart so onStop can read it synchronously.
    private var lockActive = false
    // True only while the app is actually unlocked on screen. Leaving it in that state is what
    // starts the lock timeout; leaving it while the lock is still up must not, or backing out
    // of the PIN screen and returning within the timeout would skip it.
    private var unlocked = false

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
                                unlocked = true
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
        // Only cover when a lock is configured; otherwise the cover has nothing to hide and
        // would just flash on every foreground while onStart asynchronously clears it.
        if (lockActive) setPrivacy(true)
        // The timeout counts from this moment: before, it counted from the last unlock, so
        // a user who had been working in the app longer than the timeout was asked again after
        // any brief trip out — the file picker, opening a download.
        if (unlocked && !isAuthenticating) {
            (application as SshBorgApp).lastAuthTime = System.currentTimeMillis()
        }
        if (BuildConfig.DEBUG) Log.d("AppLock", "onStop: unlocked=$unlocked authenticating=$isAuthenticating")
        unlocked = false
    }

    override fun onStart() {
        super.onStart()
        // Guard against re-entry: DEVICE_CREDENTIAL auth starts a new Activity which causes
        // onStop/onStart to fire while the original authenticate() call is still suspended.
        if (isAuthenticating) return
        unlocked = false   // until the check below says otherwise
        val app = application as SshBorgApp
        val pickerTrip = app.consumePickerTrip()
        lifecycleScope.launch {
            val mode = app.appPreferences.lockMode.first()
            lockActive = mode != AppPreferences.LOCK_NONE
            if (mode == AppPreferences.LOCK_NONE) {
                unlocked = true
                showAppLock.value = false
                setPrivacy(false)
                return@launch
            }
            val setTimeoutMs = app.appPreferences.lockTimeoutSeconds.first() * 1_000L
            // Back from a system picker the app opened: allow a few minutes, if the setting is shorter.
            val timeoutMs = if (pickerTrip) maxOf(setTimeoutMs, PICKER_GRACE_MS) else setTimeoutMs
            val elapsed = System.currentTimeMillis() - app.lastAuthTime
            if (BuildConfig.DEBUG) Log.d("AppLock", "onStart: away ${elapsed}ms, timeout ${timeoutMs}ms, picker=$pickerTrip, stamped=${app.lastAuthTime > 0L}")
            if (app.lastAuthTime > 0L && elapsed <= timeoutMs) {
                unlocked = true
                showAppLock.value = false
                setPrivacy(false)
                return@launch
            }
            if (mode == AppPreferences.LOCK_SECRET) {
                val kind = app.appLockManager.kind()
                if (kind == null) {
                    // Mode selected but no secret stored — nothing to check, don't lock out.
                    lockActive = false
                    unlocked = true
                    showAppLock.value = false
                    setPrivacy(false)
                    return@launch
                }
                lockKind.value = kind
                initialLockoutSeconds.value = app.appLockManager.lockoutRemainingSeconds()
                unlocked = false
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
                unlocked = true
                setPrivacy(false)
            } else {
                finish()
            }
        }
    }
}
