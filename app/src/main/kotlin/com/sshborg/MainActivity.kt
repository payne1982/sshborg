package com.sshborg

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.sshborg.data.AppLockManager
import com.sshborg.data.AppPreferences
import com.sshborg.ui.lock.AppLockScreen
import com.sshborg.ui.lock.SystemLockScreen
import com.sshborg.ui.theme.SshBorgTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    /**
     * Opaque cover over the whole window. It hides app content while the app is stopped — the
     * app switcher, another app, the device-credential screen — and until the first frame after
     * it comes back. It is a plain View so its visibility can be flipped without waiting for a
     * recomposition, which also means nothing on screen can ever clear it: [onResume] therefore
     * always does, because a window in front of the user must never be covered. Every earlier
     * version of this cover had its removal on a conditional branch instead, and a branch that
     * was missed left the app showing a blank wall until the process was killed.
     */
    private var privacyOverlay: View? = null

    /** Whether a lock is configured, so [onStop] knows whether there is anything to hide.
     *  Read from the lock decision, not from the preferences, so [onStop] never waits. */
    @Volatile private var lockConfigured = false

    // True only while the app is actually unlocked on screen. Leaving it in that state is what
    // starts the lock timeout; leaving it while the lock is still up must not, or backing out
    // of the PIN screen and returning within the timeout would skip it.
    private var unlocked = false

    /** What the lock gate draws, or null when the app is open. Drawn over the live app content. */
    private val gate = mutableStateOf<Gate?>(null)
    private val initialLockoutSeconds = mutableStateOf(0L)

    /** The pending system-prompt attempt; cancelling it also dismisses the prompt. */
    private var unlockJob: Job? = null
    /** The system prompt opens by itself once per lock; after that it is the gate's button. */
    private var promptedForGate = false

    private sealed interface Gate {
        /** Our own PIN / passphrase entry ([AppPreferences.LOCK_SECRET]). */
        data class Secret(val kind: AppLockManager.Kind) : Gate
        /** The system biometric / device-credential prompt, over our own opaque screen. */
        data class System(val allowDeviceCredential: Boolean) : Gate
    }

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
                // The cover has to match the theme the user is looking at, which is Compose's:
                // the Activity theme follows the system, not the in-app Light/Dark setting, so a
                // colour resolved once from the Activity theme can be white in a dark app.
                val coverColor = MaterialTheme.colorScheme.background
                LaunchedEffect(coverColor) { privacyOverlay?.setBackgroundColor(coverColor.toArgb()) }
                Box(Modifier.fillMaxSize()) {
                    AppNavigation()
                    // Opaque lock gate on top of (and preserving) the live app content.
                    when (val g = gate.value) {
                        is Gate.Secret -> {
                            val app = application as SshBorgApp
                            AppLockScreen(
                                kind = g.kind,
                                verify = { app.appLockManager.verify(it) },
                                onUnlocked = { unlockNow() },
                                initialLockoutSeconds = initialLockoutSeconds.value,
                            )
                        }
                        is Gate.System -> SystemLockScreen(
                            onUnlock = { requestSystemUnlock(g.allowDeviceCredential) },
                        )
                        null -> Unit
                    }
                    // While the gate is up, Back must not reach the app underneath: it would
                    // navigate — or close — a screen the user cannot see. Leaving the app is
                    // still allowed, it just keeps the sessions and the lock.
                    if (gate.value != null) BackHandler { moveTaskToBack(true) }
                }
            }
        }
        // Plain View on top of Compose — visibility controlled directly, no recomposition involved.
        // Clickable so that content it hides cannot be typed into or tapped blind.
        val tv = TypedValue()
        theme.resolveAttribute(android.R.attr.colorBackground, tv, true)
        val overlay = View(this).apply {
            setBackgroundColor(tv.data)
            isClickable = true
        }
        window.addContentView(overlay, android.view.ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        privacyOverlay = overlay
    }

    private fun setPrivacy(locked: Boolean) {
        privacyOverlay?.visibility = if (locked) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        // The invariant that keeps the cover from ever getting stuck: a window in front of the
        // user is never covered. What has to stay hidden while locked is hidden by the gate,
        // which is opaque and is already part of this frame (onStart decided synchronously).
        setPrivacy(false)
        // The system prompt is asked for from here, never from onStart: asking before the window
        // is resumed is how it could end up never shown and never answered.
        (gate.value as? Gate.System)?.let {
            if (!promptedForGate) requestSystemUnlock(it.allowDeviceCredential)
        }
        // In every build, not only release: FLAG_SECURE also decides whether Android keeps a
        // picture of the app for the switcher, which is what shows on the way back before the
        // lock gate is drawn — so a debug build without it is not the app we ship, and this is
        // the one place where that difference hides. Screenshots are still available when they
        // are wanted, through the setting this reads.
        lifecycleScope.launch {
            val allow = (application as SshBorgApp).appPreferences.allowScreenshots.first()
            if (allow) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Cover content when the app is actually stopped (backgrounded, recents, another
        // full-screen app, or the device-credential auth Activity) so it isn't shown on
        // return until onResume clears it. Deliberately NOT in onPause: a mere pause that
        // never reaches onStop — e.g. Gboard's voice-input panel or a transient dialog —
        // is not followed by onStart. The recents thumbnail is protected independently by
        // FLAG_SECURE (see onResume). Only cover when a lock is configured; otherwise the
        // cover has nothing to hide and would just flash on every foreground.
        if (lockConfigured) setPrivacy(true)
        // The timeout counts from this moment: before, it counted from the last unlock, so
        // a user who had been working in the app longer than the timeout was asked again after
        // any brief trip out — the file picker, opening a download.
        if (unlocked) (application as SshBorgApp).lastAuthTime = System.currentTimeMillis()
        unlocked = false
        // A trip out of the app earns the system prompt one more automatic chance on the way
        // back. Not while the prompt is what stopped us — a cancelled device-credential screen
        // would reopen itself for ever — and there the gate's own button is already waiting.
        if (unlockJob?.isActive != true) promptedForGate = false
    }

    override fun onStart() {
        super.onStart()
        val app = application as SshBorgApp
        val pickerTrip = app.consumePickerTrip()
        // Decided here and now, from the mirrored lock state, so the first frame after this
        // already carries the gate and no content is ever shown behind it.
        app.appPreferences.lockSnapshotOrNull()?.let { applyLock(it, pickerTrip) }
        lifecycleScope.launch {
            // The mirror is rewritten by every lock setting, so this normally confirms what was
            // just decided; it is what decides on the first start after the update, when there
            // is no mirror yet. Applying it twice is harmless: the same gate is the same value.
            applyLock(app.appPreferences.lockSnapshot(), pickerTrip)
        }
    }

    /** Puts the gate up or takes it down for [snap], the lock state as it is now. */
    private fun applyLock(snap: AppPreferences.LockSnapshot, pickerTrip: Boolean) {
        val app = application as SshBorgApp
        lockConfigured = snap.mode != AppPreferences.LOCK_NONE
        if (!lockConfigured) { openUp(); return }

        val setTimeoutMs = snap.timeoutSeconds * 1_000L
        // Back from a system picker the app opened: allow a few minutes, if the setting is shorter.
        val timeoutMs = if (pickerTrip) maxOf(setTimeoutMs, PICKER_GRACE_MS) else setTimeoutMs
        if (app.lastAuthTime > 0L && System.currentTimeMillis() - app.lastAuthTime <= timeoutMs) {
            openUp()
            return
        }
        if (snap.mode == AppPreferences.LOCK_SECRET) {
            val kind = AppLockManager.Kind.from(snap.secretKind)
            if (kind == null) {
                // Mode selected but no secret stored — nothing to check, don't lock the user out.
                lockConfigured = false
                openUp()
                return
            }
            raise(Gate.Secret(kind))
            return
        }
        raise(Gate.System(allowDeviceCredential = snap.mode == AppPreferences.LOCK_DEVICE))
    }

    /** Shows [g], unless that gate is already the one on screen (which would reset it). */
    private fun raise(g: Gate) {
        if (gate.value == g) return
        unlocked = false
        promptedForGate = false
        // Cleared before the gate composes, so it can't start on the remains of an earlier
        // lockout; the real value follows from disk a moment later.
        initialLockoutSeconds.value = 0L
        gate.value = g
        if (g is Gate.System) {
            // Normally the prompt is opened by onResume, which runs after the gate is up. When
            // the gate arrives later than that — the asynchronous decision, on the first start
            // after an update, with no mirror to read — this is what opens it, or the user would
            // be left looking at the gate's button with no prompt ever having appeared.
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                requestSystemUnlock(g.allowDeviceCredential)
            }
            return
        }
        if (g is Gate.Secret) {
            // Read after the gate is up: the countdown is worth a frame's delay, an unlocked
            // screen behind an unread preference is not.
            lifecycleScope.launch {
                initialLockoutSeconds.value =
                    (application as SshBorgApp).appLockManager.lockoutRemainingSeconds()
            }
        }
    }

    /** No gate needed: the app is in front of the user, open. */
    private fun openUp() {
        unlocked = true
        gate.value = null
        setPrivacy(false)
    }

    /** Authentication succeeded: open up and restart the timeout. */
    private fun unlockNow() {
        (application as SshBorgApp).lastAuthTime = System.currentTimeMillis()
        unlockJob?.cancel()
        unlockJob = null
        openUp()
    }

    /**
     * Opens the system biometric / device-credential prompt over our own gate. Any previous
     * attempt is cancelled first: a prompt that was never shown would otherwise hold a
     * suspended call for ever and the gate's button would do nothing.
     *
     * A refusal leaves the gate up, with its button. It deliberately does not close the app
     * any more: a mistaken tap on Cancel used to take the live SSH sessions with it.
     */
    private fun requestSystemUnlock(allowDeviceCredential: Boolean) {
        promptedForGate = true
        unlockJob?.cancel()
        unlockJob = lifecycleScope.launch {
            if (BiometricHelper.authenticate(this@MainActivity, allowDeviceCredential)) unlockNow()
        }
    }
}
