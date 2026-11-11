package com.sshborg.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every preference is either in the backup or on the list of ones deliberately left out.
 *
 * This exists because three of them were not: `terminal_bar_auto_hide` and
 * `terminal_hide_status_bar` arrived with the retracting title bar and `sftp_sort_dirs_first` with
 * the folder-sort setting, and each went several releases without ever being exported. Nothing
 * failed, nothing was logged, and a restored phone simply came back with those three at their
 * defaults — the kind of fault that is only ever found by someone asking "did we forget anything?".
 *
 * So the question is asked here instead, and it is asked of the **source**: `Keys` is private, the
 * class needs a `Context`, and what matters is the list of names, not the behaviour of DataStore.
 * Adding a key now forces a decision — put it in the backup, or write it below with a reason.
 */
class SettingsBackupCoverageTest {

    /**
     * Preferences that must **not** travel in a backup. The reasons are in the KDoc of
     * `exportSettingsJson`; in short: the app lock, what is bound to this device, and answers the
     * person has already given once.
     */
    private val leftOutOnPurpose = setOf(
        "biometric_lock",
        "lock_mode",
        "lock_secret_kind",
        "lock_secret_salt",
        "lock_secret_hash",
        "lock_secret_iterations",
        "lock_failed_attempts",
        "lock_lockout_until",
        "keystore_encryption",
        "root_warning_acknowledged",
        "security_reminder_dismissed",
        "privacy_policy_accepted",
    )

    private val source: String by lazy {
        val file = File("src/main/kotlin/com/sshborg/data/AppPreferences.kt")
        assertTrue("AppPreferences.kt not found at ${file.absolutePath} — has it moved?", file.isFile)
        file.readText()
    }

    private val declared: List<String> by lazy {
        Regex("""\w+PreferencesKey\("([^"]+)"\)""").findAll(source).map { it.groupValues[1] }.toList()
    }

    private val exported: Set<String> by lazy {
        Regex("""put\("([^"]+)"""").findAll(source).map { it.groupValues[1] }.toSet()
    }

    private val imported: Set<String> by lazy {
        (Regex("""obj\.has\("([^"]+)"\)""").findAll(source) +
            Regex("""obj\.optJSONArray\("([^"]+)"\)""").findAll(source))
            .map { it.groupValues[1] }.toSet()
    }

    @Test fun `the source still looks the way this test reads it`() {
        // If any of these three goes to zero the test above would pass by finding nothing, which
        // is the one way a test like this can lie.
        assertTrue("no preference keys found at all", declared.size > 20)
        assertTrue("nothing looks exported", exported.size > 10)
        assertTrue("nothing looks imported", imported.size > 10)
        assertEquals("a key is declared twice", declared.size, declared.toSet().size)
    }

    @Test fun `every preference is either backed up or deliberately left out`() {
        val missing = declared.filter { it !in exported && it !in leftOutOnPurpose }
        assertEquals(
            "these preferences are in neither the backup nor the left-out list: $missing — " +
                "add them to exportSettingsJson/importSettingsJson, or to this test with a reason",
            emptyList<String>(), missing,
        )
    }

    @Test fun `what is exported can be read back`() {
        val writeOnly = exported.filter { it !in imported }
        assertEquals(
            "exported but never imported, so a restore would silently drop them: $writeOnly",
            emptyList<String>(), writeOnly.sorted(),
        )
    }

    @Test fun `the left-out list has no stale entries`() {
        val contradicted = leftOutOnPurpose.filter { it in exported }
        assertEquals(
            "listed as left out, but exported anyway: $contradicted", emptyList<String>(), contradicted,
        )
        val unknown = leftOutOnPurpose.filter { it !in declared }
        assertEquals(
            "listed as left out, but no such preference exists any more: $unknown",
            emptyList<String>(), unknown.sorted(),
        )
    }
}
