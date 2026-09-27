package com.sshborg.ui

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

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    @Before fun arrange() = TestApp.asReturningUser()

    @After fun unlockAgain() = runBlocking {
        TestApp.app.appLockManager.clear()
        TestApp.app.appPreferences.setLockMode(AppPreferences.LOCK_NONE)
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
    }
}
