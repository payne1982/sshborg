package com.sshborg.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sshborg.MainActivity
import com.sshborg.R
import com.sshborg.TestApp
import com.sshborg.data.db.HostEntity
import com.sshborg.isTouchless
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The pass nobody can do by hand: walking the host list with a remote and checking that every
 * control can be reached and that focus never gets stuck.
 *
 * This is issue #3's whole family of bugs — a field that could not be edited without a touch, a
 * row whose menu no remote could open, a focus that went somewhere invisible. Driving a D-pad by
 * hand means holding a key and staring at a television, which is why it kept being postponed; a
 * machine can do it in fifteen seconds.
 *
 * Runs only where a D-pad is the way in: on the TV emulator. On a phone it skips itself, because
 * there focus is not even requested and the walk would prove nothing.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DpadWalkTest {

    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)
    private val seeded = mutableListOf<HostEntity>()

    @Before fun arrange() {
        org.junit.Assume.assumeTrue(
            "not a touchless device: a D-pad walk only means something on a TV",
            isTouchless(context),
        )
        TestApp.asReturningUser()
        // Two hosts, so the list has rows to walk and not just an empty state.
        runBlocking {
            listOf("alfa-tv", "beta-tv").forEach { label ->
                val id = TestApp.app.db.hostDao().upsert(
                    HostEntity(label = label, hostname = "10.0.0.1", username = "chi"),
                )
                TestApp.app.db.hostDao().getById(id)?.let(seeded::add)
            }
        }
        compose.waitForIdle()
    }

    @After fun cleanUp() = runBlocking {
        seeded.forEach { TestApp.app.db.hostDao().delete(it) }
    }

    @Test fun everyControlOnTheHostListCanBeReachedWithARemote() {
        val visited = walk()

        // What a remote must be able to land on. The rows matter most: if a host cannot be
        // focused, a television cannot open it at all.
        val wanted = listOf(
            text(R.string.hosts_manage_keys_cd),
            text(R.string.hosts_settings_cd),
            text(R.string.hosts_help_cd),
            text(R.string.hosts_add_host_cd),
            "alfa-tv",
            "beta-tv",
        )
        val missed = wanted.filter { wanted -> visited.none { it.contains(wanted) } }
        assertTrue(
            "the remote never reached: $missed\nwhat it did reach, in order: $visited" +
                "\nthe walk, key by key: " + lastTrail.joinToString("\n  ") { "${it.first} -> ${it.second}" },
            missed.isEmpty(),
        )
    }

    /**
     * From every control the remote lands on, at least one direction has to lead somewhere else.
     *
     * That is what a trap really is. Being at the bottom of a list is not one: pressing down there
     * changes nothing and is perfectly correct, which is why "focus did not move" cannot be the
     * test — the first version of this said exactly that and called the end of the list a bug.
     */
    @Test fun everyControlCanBeLeftAgain() {
        val directions = listOf(
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT,
        )
        val visited = linkedSetOf<String>()
        var trap: String? = null
        var presses = 0

        while (presses < PRESS_LIMIT) {
            val here = focusedLabel() ?: break
            visited += here
            var left = false
            for (direction in directions) {
                press(direction)
                presses++
                if (focusedLabel() != here) { left = true; break }
            }
            if (!left) { trap = here; break }
        }

        assertTrue("focus could not leave \"$trap\" in any direction; it went: $visited", trap == null)
        assertTrue("the walk only ever saw $visited, which is too little to mean anything", visited.size >= 4)
    }

    @Test fun aRemoteCanOpenTheKeysScreenAndComeBack() {
        walkUntil(text(R.string.hosts_manage_keys_cd))
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithText(text(R.string.keys_title)).assertIsDisplayed()
        press(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText(text(R.string.hosts_title)).assertIsDisplayed()
    }

    // ── the walk ────────────────────────────────────────────────────────────────

    /** Labels of everything focus landed on, in order, with the nulls dropped. */
    private fun walk(): List<String> {
        lastTrail = trail()
        return lastTrail.mapNotNull { it.second }.distinct()
    }

    private var lastTrail: List<Pair<String, String?>> = emptyList()

    /**
     * Sweeps the screen in all four directions, recording what holds focus after every press.
     *
     * All four on purpose: the first version only went down and right, decided the two top-bar
     * icons were unreachable, and was wrong — they are above the list, and a remote gets there by
     * pressing up. A walk that cannot go back up proves nothing about a trap.
     */
    private fun walkSteps(rounds: Int = 3): List<String?> = trail(rounds).map { it.second }

    /** The walk as (key pressed, what held focus afterwards) — the form that shows a trap. */
    private fun trail(rounds: Int = 3): List<Pair<String, String?>> {
        val sweep = buildList {
            repeat(8) { add(KeyEvent.KEYCODE_DPAD_DOWN to "down") }
            repeat(6) { add(KeyEvent.KEYCODE_DPAD_RIGHT to "right") }
            repeat(8) { add(KeyEvent.KEYCODE_DPAD_UP to "up") }
            repeat(6) { add(KeyEvent.KEYCODE_DPAD_LEFT to "left") }
        }
        val seen = mutableListOf<Pair<String, String?>>()
        repeat(rounds) {
            sweep.forEach { (key, name) ->
                press(key)
                seen += name to focusedLabel()
            }
        }
        return seen
    }

    /** Sweeps until [label] holds focus, and fails if it never does. */
    private fun walkUntil(label: String) {
        val sweep = buildList {
            repeat(10) { add(KeyEvent.KEYCODE_DPAD_UP) }
            repeat(4) { add(KeyEvent.KEYCODE_DPAD_RIGHT) }
            repeat(4) { add(KeyEvent.KEYCODE_DPAD_LEFT) }
        }
        repeat(3) {
            sweep.forEach { key ->
                if (focusedLabel()?.contains(label) == true) return
                press(key)
            }
        }
        if (focusedLabel()?.contains(label) != true) {
            throw AssertionError("the remote never reached \"$label\"; it is on \"${focusedLabel()}\"")
        }
    }

    /**
     * A key as a remote sends it: through the instrumentation, so it travels the whole dispatch
     * chain the device uses. Injecting into Compose instead would skip what the Activity and the
     * views do with a key first — which on a TV is where the Menu key and Back are handled.
     */
    private fun press(keyCode: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.sendKeyDownUpSync(keyCode)
        instrumentation.waitForIdleSync()
        compose.waitForIdle()
    }

    /** What is focused now, named the way the user would name it. */
    private fun focusedLabel(): String? {
        val node = compose.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull() ?: return null
        return node.label() ?: "unnamed@${node.id}"
    }

    private fun SemanticsNode.label(): String? =
        config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
            ?: config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }

    private companion object {
        /** Enough presses to wander the screen without running for ever. */
        const val PRESS_LIMIT = 80
    }
}
