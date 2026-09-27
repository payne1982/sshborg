package com.sshborg.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sshborg.MainActivity
import com.sshborg.R
import com.sshborg.Screenshots
import com.sshborg.TestApp
import com.sshborg.data.db.GroupEntity
import com.sshborg.data.db.HostEntity
import com.sshborg.data.db.SshKeyEntity
import com.sshborg.data.ssh.SshManager
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Photographs the app for the store listing, on a device rather than by hand.
 *
 * The screenshots that are in the repository were taken one at a time on real phones over a year,
 * which is why they are five different sizes; these come out identical every time, in the language
 * the emulator is set to, and can be retaken after any change to the interface.
 *
 * Everything on screen is made up here, and deliberately so: the hosts are named after nothing and
 * live in 192.0.2.0/24, the range reserved for documentation, so a published picture cannot point
 * at a real machine. See [Screenshots] for why this does not run with the other tests.
 */
@Screenshots
@RunWith(AndroidJUnit4::class)
class StoreScreenshots {

    // An empty rule, so the activity can be launched by hand after the preferences are set:
    // "allow screenshots" has to be on before the window is first resumed, or FLAG_SECURE is
    // already applied and every picture comes back black — the app blocking its own photographer.
    @get:Rule val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun text(id: Int) = context.getString(id)

    private val hosts = mutableListOf<HostEntity>()
    private val groups = mutableListOf<GroupEntity>()
    private val keys = mutableListOf<SshKeyEntity>()

    @Before fun arrangeAPlausibleApp() {
        // Whatever is already in the app would be in the pictures, and on a device somebody has
        // been using by hand that means their own machines and addresses. Better to skip.
        val alreadyThere = runBlocking { TestApp.app.db.hostDao().getAllOnce() }
        org.junit.Assume.assumeTrue(
            "this device's app already holds ${alreadyThere.size} hosts; they would end up in the " +
                "screenshots, so clear the app's data first",
            alreadyThere.isEmpty(),
        )
        TestApp.asReturningUser()
        runBlocking { TestApp.app.appPreferences.setAllowScreenshots(true) }
        runBlocking {
            val dao = TestApp.app.db
            val servers = dao.groupDao().upsert(GroupEntity(name = "Servers", color = GroupEntity.SWATCHES[0]))
            val home = dao.groupDao().upsert(GroupEntity(name = "Home", color = GroupEntity.SWATCHES[2]))
            listOf(
                Triple("web-01", "deploy", servers),
                Triple("db-01", "postgres", servers),
                Triple("backup-nas", "admin", home),
                Triple("raspberry-pi", "pi", home),
                Triple("vps-eu", "root", null),
            ).forEachIndexed { index, (label, user, group) ->
                val id = dao.hostDao().upsert(
                    HostEntity(
                        label = label,
                        hostname = "192.0.2.${10 + index}",
                        username = user,
                        groupId = group,
                        lastConnected = System.currentTimeMillis() - index * 3_600_000L,
                        connectCount = 20 - index * 3,
                    ),
                )
                dao.hostDao().getById(id)?.let(hosts::add)
            }
            listOf("laptop" to "ed25519", "deploy key" to "rsa").forEach { (label, type) ->
                val (privatePem, publicKey) = SshManager.generateKeyPair(type, comment = label)
                val id = dao.sshKeyDao().upsert(
                    SshKeyEntity(label = label, keyType = type, privateKeyPem = privatePem, publicKey = publicKey),
                )
                dao.sshKeyDao().getById(id)?.let(keys::add)
            }
            groups += dao.groupDao().getAllOnce()
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    @After fun putItBack() = runBlocking {
        scenario.close()
        TestApp.app.appPreferences.setAllowScreenshots(false)
        val dao = TestApp.app.db
        keys.forEach { dao.sshKeyDao().delete(it) }
        hosts.forEach { dao.hostDao().delete(it) }
        groups.forEach { dao.groupDao().delete(it) }
    }

    @Test fun takeThem() {
        compose.onNodeWithText(text(R.string.hosts_title)).assertIsDisplayed()
        // The rows arrive from the database a moment after the screen does, and a photograph
        // taken before that shows "No hosts yet" — which is how the first run of this came out.
        awaitText("web-01")
        shoot("01-hosts")

        compose.onNodeWithContentDescription(text(R.string.hosts_manage_keys_cd)).performClick()
        compose.onNodeWithText(text(R.string.keys_title)).assertIsDisplayed()
        awaitText("laptop")
        shoot("02-keys")
        back()

        compose.onNodeWithContentDescription(text(R.string.hosts_settings_cd)).performClick()
        compose.onNodeWithText(text(R.string.settings_title)).assertIsDisplayed()
        shoot("03-settings")
        compose.onNodeWithText(text(R.string.settings_app_lock_title)).performScrollTo()
        shoot("04-settings-security")
        back()

        compose.onNodeWithContentDescription(text(R.string.hosts_add_host_cd)).performClick()
        compose.waitForIdle()
        shoot("05-add-host")
        back()

        compose.onNodeWithText(text(R.string.hosts_title)).assertIsDisplayed()
    }

    private companion object {
        /** Long enough for the frame behind the semantics tree to be on screen. */
        const val SETTLE = 500L
    }

    private fun awaitText(what: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(what, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun back() {
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /** The whole screen, status bar included, as a listing shows it. */
    private fun shoot(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        // The semantics tree is updated in composition; the pixels reach the compositor after
        // that, and takeScreenshot photographs the pixels. Waiting for a node to exist is
        // therefore not enough — the first run of this caught the host list still empty on screen
        // while the test could already see the rows. A settle costs a moment and is the
        // difference between a listing picture and a picture of nothing.
        Thread.sleep(SETTLE)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
            ?: throw AssertionError("$name: the screen could not be photographed")
        val directory = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
