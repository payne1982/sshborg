package com.sshborg

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object BiometricHelper {

    /** Returns true if the device has biometric or device credential authentication available. */
    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    /** True if a strong biometric is enrolled and usable right now. */
    private fun biometricAvailable(context: Context): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Shows an authentication prompt. Suspends until the user authenticates,
     * cancels, or exhausts all attempts.
     *
     * Biometric-only by design (no PIN/pattern fallback). The device credential is
     * accepted only when no usable biometric is enrolled, so a user who enabled the
     * lock and later removed all fingerprints is not locked out of the app forever.
     * @return true on success, false on cancellation or permanent failure.
     */
    suspend fun authenticate(activity: FragmentActivity): Boolean =
        suspendCancellableCoroutine { cont ->
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resume(true)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (cont.isActive) cont.resume(false)
                }
                // onAuthenticationFailed = wrong biometric but user can retry — don't resume
            }

            val prompt = BiometricPrompt(activity, callback)
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.biometric_prompt_title))
                .setSubtitle(activity.getString(R.string.biometric_prompt_subtitle))
                .apply {
                    if (biometricAvailable(activity)) {
                        // A negative button is mandatory when DEVICE_CREDENTIAL is not allowed
                        setAllowedAuthenticators(BIOMETRIC_STRONG)
                        setNegativeButtonText(activity.getString(R.string.action_cancel))
                    } else {
                        setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                    }
                }
                .build()

            prompt.authenticate(info)
        }
}
