package com.sshborg.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sshborg.MainActivity
import com.sshborg.R
import com.sshborg.TestApp
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * That the app opens and its screens can be reached and left again. It sounds like nothing, and it
 * is the check that would have caught the worst of what shipped here: a screen nobody could get
 * out of, and a wall over the whole app.
 */
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    @Before fun arrange() = TestApp.asReturningUser()

    /** The system back button, through the dispatcher the app itself listens on. */
    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test fun theKeysAndSettingsScreensCanBeOpenedAndLeft() {
        compose.onNodeWithText(text(R.string.hosts_title)).assertIsDisplayed()

        compose.onNodeWithContentDescription(text(R.string.hosts_manage_keys_cd)).performClick()
        compose.onNodeWithText(text(R.string.keys_title)).assertIsDisplayed()
        pressBack()
        compose.onNodeWithText(text(R.string.hosts_title)).assertIsDisplayed()

        compose.onNodeWithContentDescription(text(R.string.hosts_settings_cd)).performClick()
        compose.onNodeWithText(text(R.string.settings_title)).assertIsDisplayed()
        pressBack()
        compose.onNodeWithText(text(R.string.hosts_title)).assertIsDisplayed()
    }
}
