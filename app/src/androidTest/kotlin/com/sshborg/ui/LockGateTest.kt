package com.sshborg.ui

import android.app.UiAutomation
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sshborg.MainActivity
import com.sshborg.R
import com.sshborg.TestApp
import com.sshborg.data.AppLockManager
import com.sshborg.data.AppPreferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The in-app lock, from the outside: the gate has to stand in front of the app's content, refuse a
 * wrong PIN out loud, and get out of the way for the right one.
 *
 * Worth having as a UI test rather than a unit one, because what broke here was never the hashing:
 * it was what is on screen. A cover that could not be cleared hid the whole app for months, and
 * before that a lock screen was drawn with its buttons under the keyboard.
 *
 * The activity is launched by hand, after the preconditions are set: a rule would launch it first,
 * and the app decides whether to lock while it is starting.
 */
@RunWith(AndroidJUnit4::class)
class LockGateTest {

    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun text(id: Int) = context.getString(id)

    @Before fun arrange() = TestApp.asReturningUser()

    @After fun unlockAgain() = runBlocking {
        TestApp.app.appLockManager.clear()
        TestApp.app.appPreferences.setLockMode(AppPreferences.LOCK_NONE)
        TestApp.app.appPreferences.setLockTimeoutSeconds(AppPreferences.DEFAULT_LOCK_TIMEOUT_SECONDS)
        // Whatever the test did to the device, hand it back as it was found.
        instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
        shell("cmd uimode night auto")
    }

    @Test fun aPinGateStandsBetweenTheAppAndItsHosts() {
        runBlocking {
            TestApp.app.appLockManager.setSecret(AppLockManager.Kind.PIN, PIN.toCharArray())
            TestApp.app.appPreferences.setLockMode(AppPreferences.LOCK_SECRET)
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText(text(R.string.lock_title)).assertIsDisplayed()

            type("0000")
            submit()
            // Verifying is 210k rounds of PBKDF2 in a coroutine Compose does not know about, so
            // the answer arrives a moment later — on an emulator, a noticeable one.
            awaitText(R.string.lock_incorrect)
            compose.onNodeWithText(text(R.string.lock_incorrect)).assertIsDisplayed()

            type(PIN)
            submit()
            compose.waitUntil(TIMEOUT) {
                compose.onAllNodesWithText(text(R.string.lock_title)).fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithText(text(R.string.hosts_title)).assertIsDisplayed()
        }
    }

    /** With no lock set there is nothing in the way, which is the other half of the same claim. */
    @Test fun withoutALockTheAppOpensStraightOnTheHostList() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText(text(R.string.hosts_title)).assertIsDisplayed()
            compose.onAllNodesWithText(text(R.string.lock_title)).fetchSemanticsNodes().let {
                assert(it.isEmpty()) { "the lock gate is up with no lock configured" }
            }
        }
    }

    /**
     * Rotating the phone is not leaving the app.
     *
     * With the timeout on "Immediately" every recreation of the activity looked like a departure,
     * so a rotation locked the app; and on a phone whose biometric prompt is portrait-only that
     * became a loop — the prompt turned the screen, the turn recreated the activity, the activity
     * asked for the prompt again. The cure is one line of manifest, so both halves are checked
     * here: the same activity instance has to come out of the rotation, and the gate must stay down.
     */
    @Test fun rotatingDoesNotCountAsLeavingTheApp() {
        withAPinAndNoGrace {
            val before = identity()
            val orientationBefore = orientation()
            instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_90)
            compose.waitForIdle()
            Thread.sleep(SETTLE)
            assumeTrue(
                "this device did not turn (a television is landscape and stays there), so there " +
                    "is no configuration change to survive",
                orientation() != orientationBefore,
            )

            assertEquals(
                "the rotation destroyed and rebuilt the activity: android:configChanges on " +
                    "MainActivity no longer covers it",
                before,
                identity(),
            )
            assertGateIsDown()
        }
    }

    /** The same claim for the other configuration change that used to rebuild everything. */
    @Test fun switchingTheSystemThemeDoesNotCountEither() {
        withAPinAndNoGrace {
            val before = identity()
            shell("cmd uimode night yes")
            compose.waitForIdle()
            Thread.sleep(SETTLE)
            assertEquals(
                "switching the system theme destroyed and rebuilt the activity",
                before,
                identity(),
            )
            assertGateIsDown()
        }
    }

    /**
     * An unlocked app with the strictest setting there is: a PIN, and a timeout of zero, where
     * anything the app reads as "the user left" locks it at once.
     */
    private fun withAPinAndNoGrace(block: ActivityScenario<MainActivity>.() -> Unit) {
        runBlocking {
            TestApp.app.appLockManager.setSecret(AppLockManager.Kind.PIN, PIN.toCharArray())
            TestApp.app.appPreferences.setLockMode(AppPreferences.LOCK_SECRET)
            TestApp.app.appPreferences.setLockTimeoutSeconds(0)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithText(text(R.string.lock_title)).assertIsDisplayed()
            type(PIN)
            submit()
            compose.waitUntil(TIMEOUT) {
                compose.onAllNodesWithText(text(R.string.lock_title)).fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithText(text(R.string.hosts_title)).assertIsDisplayed()
            scenario.block()
        }
    }

    private fun assertGateIsDown() {
        compose.onNodeWithText(text(R.string.hosts_title)).assertIsDisplayed()
        val gate = compose.onAllNodesWithText(text(R.string.lock_title)).fetchSemanticsNodes()
        assert(gate.isEmpty()) { "the lock gate came up on a configuration change" }
    }

    /** Which object the activity is, to catch a rebuild that looks identical on screen. */
    private fun ActivityScenario<MainActivity>.identity(): Int {
        var id = 0
        onActivity { id = System.identityHashCode(it) }
        return id
    }

    private fun ActivityScenario<MainActivity>.orientation(): Int {
        var value = Configuration.ORIENTATION_UNDEFINED
        onActivity { value = it.resources.configuration.orientation }
        return value
    }

    /** Read to the end before closing: closing our side of the pipe early can kill the command. */
    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)
        ).use { it.readBytes() }
    }

    private fun awaitText(id: Int) = compose.waitUntil(TIMEOUT) {
        compose.onAllNodesWithText(text(id)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun type(digits: String) =
        digits.forEach { compose.onNodeWithText(it.toString()).performClick() }

    private fun submit() =
        compose.onNodeWithContentDescription(text(R.string.lock_submit_cd)).performClick()

    private companion object {
        const val PIN = "4913"
        const val TIMEOUT = 5_000L
        /** A configuration change is not instant, and nothing fires an event when it is done. */
        const val SETTLE = 1_500L
    }
}
