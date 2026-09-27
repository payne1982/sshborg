package com.sshborg

import androidx.test.platform.app.InstrumentationRegistry
import com.sshborg.data.AppPreferences
import kotlinx.coroutines.runBlocking

/**
 * The app under test, and the state a UI test needs it to be in before it starts.
 *
 * Instrumented tests run inside the app's own process, so they can reach its preferences
 * directly — which is the only sane way to arrange a precondition like "a lock is set".
 */
internal object TestApp {

    val app: SshBorgApp
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SshBorgApp

    /**
     * The app as a returning user finds it: the one-time dialogs already answered, no lock. A
     * fresh install shows the privacy policy over everything, and a test that does not settle
     * that first is testing the dialog.
     */
    fun asReturningUser() = runBlocking {
        with(app.appPreferences) {
            setPrivacyPolicyAccepted()
            setRootWarningAcknowledged()
            setSecurityReminderDismissed()
            setLockMode(AppPreferences.LOCK_NONE)
        }
        app.appLockManager.clear()
        app.lastAuthTime = 0L
    }
}
