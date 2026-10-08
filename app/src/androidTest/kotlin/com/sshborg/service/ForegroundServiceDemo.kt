package com.sshborg.service

import android.os.ParcelFileDescriptor
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sshborg.MainActivity
import com.sshborg.R
import com.sshborg.Screenshots
import com.sshborg.TestApp
import com.sshborg.data.db.HostEntity
import com.sshborg.data.db.SshKeyEntity
import com.sshborg.data.ssh.SshManager
import java.io.File
import java.util.Properties
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Films the thing the foreground service exists for, for Google Play's foreground-service
 * declaration: a session the user opened, the app going away, the notification that says the
 * session is still there, and the same session still running on the way back.
 *
 * Asserts almost nothing on purpose — it is a camera, not a test, which is why it carries
 * [Screenshots] and stays out of the ordinary run. `scripts/make-fgs-video.sh` records the screen
 * around it and brings the file back.
 *
 * It needs a real server, pushed onto the device as demo.properties and demo.key by that script,
 * and it needs "allow screenshots" on before the window is ever resumed: FLAG_SECURE blacks out a
 * screen recording exactly as it blacks out a screenshot.
 */
@Screenshots
@RunWith(AndroidJUnit4::class)
class ForegroundServiceDemo {

    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun text(id: Int) = context.getString(id)

    @Test fun aSessionKeepsRunningWhileTheAppIsAway() {
        val settings = File(context.getExternalFilesDir(null), "demo.properties")
        val key = File(context.getExternalFilesDir(null), "demo.key")
        assumeTrue(
            "no server to film against: run scripts/make-fgs-video.sh, which pushes demo.properties " +
                "and demo.key onto the device",
            settings.isFile && key.isFile,
        )
        val demo = Properties().apply { settings.reader().use { load(it) } }
        val host = demo.getProperty("host")
        val user = demo.getProperty("user")
        assumeTrue("demo.properties says host=$host user=$user", !host.isNullOrBlank() && !user.isNullOrBlank())

        val alreadyThere = runBlocking { TestApp.app.db.hostDao().getAllOnce() }
        assumeTrue(
            "this device's app already holds ${alreadyThere.size} hosts; they would be in the film",
            alreadyThere.isEmpty(),
        )

        TestApp.asReturningUser()
        runBlocking {
            // Before the first resume, or FLAG_SECURE is already on and the film is 45 seconds of black.
            TestApp.app.appPreferences.setAllowScreenshots(true)
            TestApp.app.appPreferences.setNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }

        val keyId = runBlocking {
            val imported = SshManager.importPrivateKey(key.readText())
            TestApp.app.db.sshKeyDao().upsert(
                SshKeyEntity(
                    label = "demo",
                    keyType = imported.keyType,
                    privateKeyPem = imported.pem,
                    publicKey = imported.publicKey,
                ),
            )
        }
        runBlocking {
            TestApp.app.db.hostDao().upsert(
                HostEntity(label = "demo-server", hostname = host, username = user, keyId = keyId),
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            // The user opens a session: one tap on the host, and the host key trusted the first time.
            awaitText("demo-server")
            compose.onNodeWithText("demo-server", substring = true).performClick()
            runCatching { awaitText(text(R.string.action_trust)) }
            if (compose.onAllNodesWithText(text(R.string.action_trust)).fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithText(text(R.string.action_trust)).performClick()
            }
            // The dialog has to be gone before anything else happens, and there has to be exactly
            // one session behind it. Three takes came back with a host-key dialog sitting over the
            // whole film while the terminal wrote underneath it — which can only mean a second
            // connection nobody asked for. Better a test that fails and says so than another film.
            compose.waitUntil(20_000) {
                compose.onAllNodesWithText(text(R.string.action_trust)).fetchSemanticsNodes().isEmpty()
            }
            Thread.sleep(4_000)
            val open = TestApp.app.sessionManager.sessions.value
            android.util.Log.i("FgsDemo", "sessions after connecting: ${open.size} ${open.map { it.hostLabel to it.status }}")
            check(open.size == 1) { "expected one session, found ${open.size}: ${open.map { it.status }}" }

            // Something whose output makes the passage of time visible: when the app comes back, the
            // gap in the timestamps is the proof that the session was never interrupted.
            line("clear")
            line("PS1='demo:~$ '")
            line("while sleep 2; do date +%T; done")
            Thread.sleep(7_000)

            // The user leaves the app, the way anyone does: home, and then the notification.
            shell("input keyevent KEYCODE_HOME")
            Thread.sleep(3_000)
            shell("cmd statusbar expand-notifications")
            Thread.sleep(7_000)   // long enough to read the session count and "Disconnect all"
            shell("cmd statusbar collapse")

            // Time with the app away. The timestamps keep arriving on the server's side.
            Thread.sleep(14_000)

            // Back the way a person comes back: the launcher's own intent, which RESUMES the task
            // instead of starting a second copy of the activity. `am start -n` does the latter, and
            // the film then showed the host list rather than the session that was left running —
            // true, but a weaker thing to show than the terminal with its output continuing.
            // The launcher's own intent, flags included: NEW_TASK | RESET_TASK_IF_NEEDED is what
            // brings an existing task back instead of stacking a second activity on top of it.
            // Not `monkey`, which launches the app and then injects a random event — that event
            // landed on a host row, opened a second connection, and left a host-key dialog over
            // the last ten seconds of two takes.
            shell(
                "am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER " +
                    "-f 0x10200000 -n ${context.packageName}/com.sshborg.MainActivity"
            )
            // And it ends there, on the session still writing. Tapping anything after this was an
            // ending too many: a tap that lands on the host list opens a second connection, and the
            // film finished on a host-key dialog instead of on the thing it is about.
            Thread.sleep(10_000)
        }
    }

    /** Writes a command into the live shell: typed keys never reach the terminal, it takes text from the IME. */
    private fun line(command: String) {
        val shell = TestApp.app.sessionManager.sessions.value
            .firstNotNullOfOrNull { it.shellSession } ?: error("no shell session is open")
        shell.write((command + "\n").toByteArray())
        Thread.sleep(700)
    }

    private fun awaitText(value: String) = compose.waitUntil(20_000) {
        compose.onAllNodesWithText(value, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)
        ).use { it.readBytes() }
    }
}
