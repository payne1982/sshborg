package com.sshborg.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import java.io.File
import java.util.Properties
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `OSC 52` from a real server to the real clipboard, through the whole app.
 *
 * This exists because the first version of the feature reached a phone broken, and every test it
 * had passed. The parser was covered, the handoff is covered now, and in between them was the
 * only part that matters to somebody using it: a server sends the escape, and the text is on the
 * clipboard. Nothing short of the whole chain — emulator, ViewModel, the host's own switch, the
 * lifecycle gate, `ClipboardManager` — can tell you that it is.
 *
 * So the session is real: the emulator opens an SSH shell on the server `demo.properties` names,
 * which `make-screenshots.sh` pushes (`emuSshHost` in `test.properties`), and the command is
 * written into **that session's transport** rather than typed. Typing is the keyboard's business
 * and has its own test; what is being measured here is what comes *back* from the far end.
 *
 * The negative half matters as much as the positive one: the same server, the same command, the
 * switch off, and the clipboard must be untouched. That is the only assertion that distinguishes
 * "the feature works" from "the feature is always on", and the switch is the whole of the
 * promise made to somebody who does not trust a machine.
 */
@RunWith(AndroidJUnit4::class)
class ClipboardFromHostTest {

    @get:Rule val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun text(id: Int) = context.getString(id)

    private var host: HostEntity? = null
    private var key: SshKeyEntity? = null

    @After fun cleanUp() {
        if (::scenario.isInitialized) scenario.close()
        TestApp.app.sessionManager.removeAll()
        runBlocking {
            host?.let { TestApp.app.db.hostDao().delete(it) }
            key?.let { TestApp.app.db.sshKeyDao().delete(it) }
        }
    }

    @Test fun aHostThatIsAllowedToWritesTheClipboard() {
        arrange(allowed = true)
        openASession()
        val sentinel = putSentinelOnTheClipboard()
        val token = "osc52-" + System.currentTimeMillis()

        sendClipboardSequence(token)

        val arrived = waitForClipboard(token)
        assertEquals(
            "the host was allowed to write to the clipboard and the text never arrived " +
                "(clipboard still: $arrived, sentinel was $sentinel)",
            token, arrived,
        )
        assertTrue(
            "and it must never be silent: the message naming it did not appear",
            compose.onAllNodesWithText(text(R.string.terminal_clipboard_from_host))
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test fun aHostThatIsNotAllowedDoesNotTouchIt() {
        arrange(allowed = false)
        openASession()
        val sentinel = putSentinelOnTheClipboard()
        val token = "osc52-" + System.currentTimeMillis()

        sendClipboardSequence(token)

        // Long enough that the positive case would have finished several times over.
        waitForClipboard(token, timeoutMs = 8_000)
        assertEquals(
            "the switch is off for this host, so nothing of ours may have touched the clipboard",
            sentinel, clipboard(),
        )
        assertTrue(
            "and nothing may have been announced either",
            compose.onAllNodesWithText(text(R.string.terminal_clipboard_from_host))
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    // ── arranging ───────────────────────────────────────────────────────────────

    private fun arrange(allowed: Boolean) {
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
            val db = TestApp.app.db
            db.hostDao().getAllOnce().filter { it.label == HOST_LABEL }.forEach { db.hostDao().delete(it) }
            db.sshKeyDao().getAllOnce().filter { it.label == KEY_LABEL }.forEach { db.sshKeyDao().delete(it) }
            val imported = SshManager.importPrivateKey(keyFile.readText())
            val keyId = db.sshKeyDao().upsert(
                SshKeyEntity(
                    label = KEY_LABEL,
                    keyType = imported.keyType,
                    privateKeyPem = imported.pem,
                    publicKey = imported.publicKey,
                ),
            )
            key = db.sshKeyDao().getById(keyId)
            val hostId = db.hostDao().upsert(
                HostEntity(
                    label = HOST_LABEL, hostname = address!!, username = user!!, keyId = keyId,
                    clipboardWrite = allowed,
                ),
            )
            host = db.hostDao().getById(hostId)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    /** The same walk `TerminalKeyboardTest` makes: tap the host, trust the key, wait for a shell. */
    private fun openASession() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(HOST_LABEL, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(HOST_LABEL, substring = true).performClick()
        runCatching {
            compose.waitUntil(20_000) {
                compose.onAllNodesWithText(text(R.string.action_trust)).fetchSemanticsNodes().isNotEmpty()
            }
        }
        if (compose.onAllNodesWithText(text(R.string.action_trust)).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText(text(R.string.action_trust)).performClick()
        }
        compose.waitUntil(40_000) {
            compose.onAllNodesWithContentDescription(text(R.string.terminal_disconnect_cd))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(40_000) {
            TestApp.app.sessionManager.sessions.value.any { it.status == SessionManager.Status.Connected }
        }
        compose.waitForIdle()
    }

    // ── the far end ─────────────────────────────────────────────────────────────

    /**
     * Has the server send `OSC 52`, by writing the command into the live session.
     *
     * Written rather than typed on purpose: a key press is the keyboard's path and is tested
     * where the keyboard is. The bytes that matter here are the ones the *shell* produces, and
     * for that it has to be a real shell on a real server — which is why the escapes in the
     * command below are the shell's to interpret, not ours.
     */
    private fun sendClipboardSequence(token: String) {
        val payload = java.util.Base64.getEncoder().encodeToString(token.toByteArray())
        val session = TestApp.app.sessionManager.sessions.value
            .first { it.status == SessionManager.Status.Connected }
        val transport = requireNonNull(session.transport, "the connected session has no transport")
        transport.write("printf '\\033]52;c;$payload\\a'\n".toByteArray())
    }

    private fun <T> requireNonNull(value: T?, message: String): T = value ?: error(message)

    // ── the clipboard, asked of the platform ────────────────────────────────────

    private fun manager(): ClipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private fun clipboard(): String? {
        var held: String? = null
        scenario.onActivity {
            held = manager().primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        }
        instrumentation.waitForIdleSync()
        return held
    }

    private fun putSentinelOnTheClipboard(): String {
        val sentinel = "untouched-" + System.currentTimeMillis()
        scenario.onActivity { manager().setPrimaryClip(ClipData.newPlainText("test", sentinel)) }
        instrumentation.waitForIdleSync()
        return sentinel
    }

    /** Waits for [token] to appear on the clipboard, and returns whatever is there in the end. */
    private fun waitForClipboard(token: String, timeoutMs: Long = 20_000): String? {
        runCatching { compose.waitUntil(timeoutMs) { clipboard() == token } }
        return clipboard()
    }

    private companion object {
        const val HOST_LABEL = "clipboard-test"
        const val KEY_LABEL = "clipboard-test-key"
    }
}
