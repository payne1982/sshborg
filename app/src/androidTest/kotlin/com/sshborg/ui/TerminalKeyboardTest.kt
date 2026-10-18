package com.sshborg.ui

import android.os.ParcelFileDescriptor
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sshborg.MainActivity
import com.sshborg.R
import com.sshborg.TestApp
import com.sshborg.data.db.HostEntity
import com.sshborg.data.db.SshKeyEntity
import com.sshborg.data.ssh.SshManager
import com.sshborg.service.SessionManager
import com.sshborg.isTouchless
import java.io.File
import java.util.Properties
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The soft keyboard in the terminal, on a device that has no touchscreen to ask for it.
 *
 * On a television the keyboard is the whole of text entry, and until now the terminal had no way
 * to call it back: the tap is what raises it, and the extra bar's keyboard key is on screen only
 * while the keyboard already is. So the remote's OK key now does what the tap does — and this
 * holds it to that, along with the two halves around it: a new session opens with the keyboard up,
 * which is the one automatic raise left in the app, and BACK is still what puts it away.
 *
 * Needs a real server, because the terminal only has the focus once a session is live: before that
 * a dialog or an overlay is in front of it. The address and key are the ones make-screenshots.sh
 * pushes onto the device (emuSshHost, sshUser and sshKeyDir/i_plain in test.properties); without
 * them this skips itself, as it does on anything with a touchscreen.
 */
@RunWith(AndroidJUnit4::class)
class TerminalKeyboardTest {

    // The activity is started by hand, after the preferences are set: launched by the rule it
    // would come up on a fresh install with the privacy dialog over everything, and key presses
    // would go to that window instead (see DpadWalkTest, where it cost two releases).
    @get:Rule val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun text(id: Int) = context.getString(id)

    private var host: HostEntity? = null
    private var key: SshKeyEntity? = null

    @Before fun arrange() {
        assumeTrue(
            "not a touchless device: there the tap already asks for the keyboard",
            isTouchless(context),
        )
        val settings = File(context.getExternalFilesDir(null), "demo.properties")
        val keyFile = File(context.getExternalFilesDir(null), "demo.key")
        assumeTrue(
            "no server on this device to open a session against: ${settings.path}",
            settings.isFile && keyFile.isFile,
        )
        val demo = Properties().apply { settings.reader().use { load(it) } }
        val address = demo.getProperty("host")
        val user = demo.getProperty("user")
        assumeTrue("demo.properties carries no host and user", !address.isNullOrBlank() && !user.isNullOrBlank())

        TestApp.asReturningUser()
        runBlocking {
            val dao = TestApp.app.db
            val imported = SshManager.importPrivateKey(keyFile.readText())
            val keyId = dao.sshKeyDao().upsert(
                SshKeyEntity(
                    label = KEY_LABEL,
                    keyType = imported.keyType,
                    privateKeyPem = imported.pem,
                    publicKey = imported.publicKey,
                ),
            )
            key = dao.sshKeyDao().getById(keyId)
            val hostId = dao.hostDao().upsert(
                HostEntity(label = HOST_LABEL, hostname = address!!, username = user!!, keyId = keyId),
            )
            host = dao.hostDao().getById(hostId)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    @After fun cleanUp() {
        if (::scenario.isInitialized) scenario.close()
        // The session outlives the screen by design, and the process outlives the test: left
        // open it would still be on the host list for whatever runs next.
        TestApp.app.sessionManager.removeAll()
        runBlocking {
            host?.let { TestApp.app.db.hostDao().delete(it) }
            key?.let { TestApp.app.db.sshKeyDao().delete(it) }
        }
    }

    @Test fun theOkKeyAsksForTheKeyboardInTheTerminal() {
        openASession()

        assertTrue(
            "a new session should come up with the keyboard already there; ${state()}",
            settleIme(up = true),
        )

        press(KeyEvent.KEYCODE_BACK)
        assertFalse(
            "BACK did not put the keyboard away, so what follows would prove nothing; ${state()}",
            settleIme(up = false),
        )

        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertTrue(
            "OK did not bring the keyboard back: on a device with no touchscreen there is then no " +
                "way to type in the terminal at all; ${state()}",
            settleIme(up = true),
        )
    }

    // ── getting there ───────────────────────────────────────────────────────────

    /** Opens a shell on the seeded host, trusting the host key the way a first visit has to. */
    private fun openASession() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(HOST_LABEL, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(HOST_LABEL, substring = true).performClick()

        // The prompt arrives a moment after the connection starts, not with it, so this waits for
        // it instead of looking once — and does not insist, because a device that has met this
        // server before is not asked again.
        runCatching {
            compose.waitUntil(20_000) {
                compose.onAllNodesWithText(text(R.string.action_trust)).fetchSemanticsNodes().isNotEmpty()
            }
        }
        if (compose.onAllNodesWithText(text(R.string.action_trust)).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText(text(R.string.action_trust)).performClick()
        }

        // The terminal's own furniture says the screen is there, which it is from the first
        // moment — the session may still be connecting behind it.
        compose.waitUntil(40_000) {
            compose.onAllNodesWithContentDescription(text(R.string.terminal_disconnect_cd))
                .fetchSemanticsNodes().isNotEmpty()
        }
        // So the live session is what is waited for, asked of the app itself: everything the
        // keyboard does here hangs off that state (TerminalScreen's sessionUp), and judging it
        // while the session is still connecting measures the wrong moment.
        compose.waitUntil(40_000) {
            TestApp.app.sessionManager.sessions.value.any {
                it.status == SessionManager.Status.Connected
            }
        }
        compose.waitForIdle()
        // And the clock is pushed on by hand. What the terminal does with the keyboard is written
        // as "wait for the view, then ask" — a `delay` inside a LaunchedEffect — and under the
        // compose test rule that delay does not pass by itself: it sat there for eight seconds of
        // real time in the run that found this, so the keyboard was never asked for and the app
        // looked broken when it was the clock standing still.
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    // ── the keyboard, measured from outside the app ─────────────────────────────

    /**
     * Whether the keyboard is on screen, asked of the window rather than of Compose.
     *
     * Deliberately not `WindowInsets.isImeVisible`: that is what the app itself reads to decide
     * what to restore, so a test that believed it could agree with a wrong answer. This asks the
     * platform for the window's own insets.
     */
    private fun imeUp(): Boolean {
        var up = false
        scenario.onActivity { activity ->
            up = ViewCompat.getRootWindowInsets(activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        instrumentation.waitForIdleSync()
        return up
    }

    /** Waits for the keyboard to reach [up], and returns where it actually ended. */
    private fun settleIme(up: Boolean): Boolean {
        repeat(SETTLE_TRIES) {
            if (imeUp() == up) return up
            Thread.sleep(POLL_MS)
            // The app's own waits are on this clock, so it has to move with the real one.
            compose.mainClock.advanceTimeBy(POLL_MS)
            compose.waitForIdle()
        }
        return imeUp()
    }

    /**
     * Everything needed to tell a refused keyboard from one nobody asked for: what the input
     * method thinks, which View holds the focus — the keyboard is asked for by the terminal's
     * own View, and if something else has the focus the request goes nowhere — and what Compose
     * believes is focused, which on a television is usually a button in the title bar.
     */
    private fun state(): String {
        var view = "none"
        scenario.onActivity { activity ->
            view = activity.window.currentFocus?.let { "${it::class.java.simpleName}#${it.id}" } ?: "none"
        }
        instrumentation.waitForIdleSync()
        val node = compose.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull()
        val label = node?.config?.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
            ?: node?.config?.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
            ?: node?.let { "unnamed@${it.id}" }
            ?: "nothing"
        val session = TestApp.app.sessionManager.sessions.value.firstOrNull()
        val remembered = session?.let { TestApp.app.sessionManager.keyboardWasUp(it.id) }
        return "the IME says: ${imeDump()}; the focused view is $view; Compose focus is on $label" +
            "; the session is ${session?.status} and its keyboard memory says $remembered" +
            "; lockGateUp=${TestApp.app.lockGateUp}"
    }

    /** What the input-method service thinks, for a failure message that can be acted on. */
    private fun imeDump(): String = runCatching {
        val pipe = instrumentation.uiAutomation.executeShellCommand("dumpsys input_method")
        ParcelFileDescriptor.AutoCloseInputStream(pipe).bufferedReader().use { it.readText() }
            .lineSequence()
            .filter { line -> INTERESTING.any { line.contains(it) } }
            .map { it.trim() }
            .distinct()
            .joinToString("; ")
            .ifBlank { "nothing in the dump about a shown keyboard" }
    }.getOrElse { "the dump could not be read: $it" }

    private fun press(keyCode: Int) {
        instrumentation.sendKeyDownUpSync(keyCode)
        instrumentation.waitForIdleSync()
        compose.waitForIdle()
    }

    private companion object {
        const val HOST_LABEL = "ok-key-host"
        const val KEY_LABEL = "ok-key"

        /** The keyboard slides, and the IME is a process of its own: it is given a moment. */
        const val SETTLE_TRIES = 30
        const val POLL_MS = 200L

        val INTERESTING = listOf("mInputShown", "mShowRequested", "mImeWindowVis", "mSystemReady")
    }
}
