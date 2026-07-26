package com.sshborg.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

class AppPreferences(private val context: Context) {

    private object Keys {
        val BIOMETRIC_LOCK           = booleanPreferencesKey("biometric_lock")
        val KEYSTORE_ENCRYPTION      = booleanPreferencesKey("keystore_encryption")
        val CONFIRM_EXIT             = booleanPreferencesKey("confirm_exit")
        val LOCK_TIMEOUT_SECONDS      = intPreferencesKey("lock_timeout_seconds")
        val ROOT_WARNING_ACKNOWLEDGED = booleanPreferencesKey("root_warning_acknowledged")
        val INVERT_TERMINAL_SCROLL    = booleanPreferencesKey("invert_terminal_scroll")
        val NIGHT_MODE                = intPreferencesKey("night_mode")
        val ALLOW_SCREENSHOTS         = booleanPreferencesKey("allow_screenshots")
        val SCROLLBACK_LINES          = intPreferencesKey("scrollback_lines")
        val TERMINAL_FONT_SIZE        = intPreferencesKey("terminal_font_size")
        val KEEP_SCREEN_ON            = booleanPreferencesKey("keep_screen_on")
        val TERMINAL_COLOR_SCHEME     = intPreferencesKey("terminal_color_scheme")
        val DOUBLE_TAP_ACTION         = intPreferencesKey("double_tap_action")
        val HISTORY_SUGGESTIONS          = booleanPreferencesKey("history_suggestions")
        val SUGGESTIONS_BAR_STICKY       = booleanPreferencesKey("suggestions_bar_sticky")
        val SECURITY_REMINDER_DISMISSED  = booleanPreferencesKey("security_reminder_dismissed")
        val PRIVACY_POLICY_ACCEPTED      = booleanPreferencesKey("privacy_policy_accepted")
    }

    val biometricLock: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.BIOMETRIC_LOCK] ?: false }

    val keystoreEncryption: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.KEYSTORE_ENCRYPTION] ?: false }

    val confirmExit: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.CONFIRM_EXIT] ?: false }

    /** Lock timeout in seconds. 0 = lock immediately on every app switch. Default 60 (1 minute). */
    val lockTimeoutSeconds: Flow<Int> =
        context.dataStore.data.map { it[Keys.LOCK_TIMEOUT_SECONDS] ?: 60 }

    val rootWarningAcknowledged: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ROOT_WARNING_ACKNOWLEDGED] ?: false }

    val invertTerminalScroll: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.INVERT_TERMINAL_SCROLL] ?: false }

    val nightMode: Flow<Int> =
        context.dataStore.data.map { it[Keys.NIGHT_MODE] ?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM }

    suspend fun setBiometricLock(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC_LOCK] = enabled }
    }

    suspend fun setKeystoreEncryption(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KEYSTORE_ENCRYPTION] = enabled }
    }

    suspend fun setConfirmExit(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CONFIRM_EXIT] = enabled }
    }

    suspend fun setLockTimeoutSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.LOCK_TIMEOUT_SECONDS] = seconds }
    }

    suspend fun setRootWarningAcknowledged() {
        context.dataStore.edit { it[Keys.ROOT_WARNING_ACKNOWLEDGED] = true }
    }

    suspend fun setInvertTerminalScroll(enabled: Boolean) {
        context.dataStore.edit { it[Keys.INVERT_TERMINAL_SCROLL] = enabled }
    }

    suspend fun setNightMode(mode: Int) {
        context.dataStore.edit { it[Keys.NIGHT_MODE] = mode }
    }

    val allowScreenshots: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ALLOW_SCREENSHOTS] ?: false }

    suspend fun setAllowScreenshots(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ALLOW_SCREENSHOTS] = enabled }
    }

    /** Number of scrollback lines kept in memory. Default 2000. */
    val scrollbackLines: Flow<Int> =
        context.dataStore.data.map { it[Keys.SCROLLBACK_LINES] ?: 2000 }

    suspend fun setScrollbackLines(lines: Int) {
        context.dataStore.edit { it[Keys.SCROLLBACK_LINES] = lines }
    }

    /** Default terminal font size in sp. 13sp ≈ the 36px the app used before this setting. */
    val terminalFontSize: Flow<Int> =
        context.dataStore.data.map { it[Keys.TERMINAL_FONT_SIZE] ?: DEFAULT_TERMINAL_FONT_SIZE }

    suspend fun setTerminalFontSize(sp: Int) {
        context.dataStore.edit { it[Keys.TERMINAL_FONT_SIZE] = sp }
    }

    /** Keep the screen awake while a terminal is open. Default false (saves battery). */
    val keepScreenOn: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.KEEP_SCREEN_ON] ?: false }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    }

    /** Terminal color scheme: dark (default), light, or following the app theme. */
    val terminalColorScheme: Flow<Int> =
        context.dataStore.data.map { it[Keys.TERMINAL_COLOR_SCHEME] ?: TERMINAL_SCHEME_DARK }

    suspend fun setTerminalColorScheme(scheme: Int) {
        context.dataStore.edit { it[Keys.TERMINAL_COLOR_SCHEME] = scheme }
    }

    /** What a double-tap on the terminal sends: nothing (default), one Tab, or two Tabs. */
    val doubleTapAction: Flow<Int> =
        context.dataStore.data.map { it[Keys.DOUBLE_TAP_ACTION] ?: DOUBLE_TAP_NONE }

    suspend fun setDoubleTapAction(action: Int) {
        context.dataStore.edit { it[Keys.DOUBLE_TAP_ACTION] = action }
    }

    companion object {
        const val DEFAULT_TERMINAL_FONT_SIZE = 13
        const val MIN_TERMINAL_FONT_SIZE = 8
        const val MAX_TERMINAL_FONT_SIZE = 32

        const val TERMINAL_SCHEME_DARK = 0
        const val TERMINAL_SCHEME_LIGHT = 1
        const val TERMINAL_SCHEME_FOLLOW_APP = 2

        const val DOUBLE_TAP_NONE = 0
        const val DOUBLE_TAP_TAB = 1
        const val DOUBLE_TAP_TAB_TWICE = 2
    }

    /** Whether to show shell history suggestions above the keyboard. Default true. */
    val historySuggestions: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.HISTORY_SUGGESTIONS] ?: true }

    suspend fun setHistorySuggestions(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HISTORY_SUGGESTIONS] = enabled }
    }

    /** Whether to keep the suggestion bar always visible (fixed height) to avoid terminal resizing. Default false. */
    val suggestionsBarSticky: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.SUGGESTIONS_BAR_STICKY] ?: false }

    suspend fun setSuggestionsBarSticky(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SUGGESTIONS_BAR_STICKY] = enabled }
    }

    val securityReminderDismissed: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.SECURITY_REMINDER_DISMISSED] ?: false }

    suspend fun setSecurityReminderDismissed() {
        context.dataStore.edit { it[Keys.SECURITY_REMINDER_DISMISSED] = true }
    }

    val privacyPolicyAccepted: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.PRIVACY_POLICY_ACCEPTED] ?: false }

    suspend fun setPrivacyPolicyAccepted() {
        context.dataStore.edit { it[Keys.PRIVACY_POLICY_ACCEPTED] = true }
    }

    // ── Settings backup ──────────────────────────────────────────────────────
    // Only portable UI/terminal preferences are backed up. Deliberately excluded:
    // biometric_lock and keystore_encryption (security gates tied to this device's
    // capabilities / actual Keystore crypto state — restoring blindly could lock
    // the user out or misrepresent whether data is encrypted), and the one-time
    // acknowledgement flags (root warning, security reminder, privacy consent),
    // which should re-appear on a fresh install rather than be auto-dismissed.

    /**
     * Complete snapshot of the backup-eligible preferences as a JSON object.
     * Every key is always written using the same default the Flow reads fall back
     * to, so a preference the user never touched (null in DataStore) still lands in
     * the backup. Otherwise restoring would be non-deterministic — it could only
     * ever reset the settings the user had already changed. Keep these defaults in
     * sync with the corresponding Flow getters above.
     */
    suspend fun exportSettingsJson(): JSONObject {
        val p = context.dataStore.data.first()
        return JSONObject().apply {
            put("confirm_exit",           p[Keys.CONFIRM_EXIT] ?: false)
            put("lock_timeout_seconds",   p[Keys.LOCK_TIMEOUT_SECONDS] ?: 60)
            put("invert_terminal_scroll", p[Keys.INVERT_TERMINAL_SCROLL] ?: false)
            put("night_mode",             p[Keys.NIGHT_MODE] ?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            put("allow_screenshots",      p[Keys.ALLOW_SCREENSHOTS] ?: false)
            put("scrollback_lines",       p[Keys.SCROLLBACK_LINES] ?: 2000)
            put("terminal_font_size",     p[Keys.TERMINAL_FONT_SIZE] ?: DEFAULT_TERMINAL_FONT_SIZE)
            put("keep_screen_on",         p[Keys.KEEP_SCREEN_ON] ?: false)
            put("terminal_color_scheme",  p[Keys.TERMINAL_COLOR_SCHEME] ?: TERMINAL_SCHEME_DARK)
            put("history_suggestions",    p[Keys.HISTORY_SUGGESTIONS] ?: true)
            put("suggestions_bar_sticky", p[Keys.SUGGESTIONS_BAR_STICKY] ?: false)
            put("double_tap_action",      p[Keys.DOUBLE_TAP_ACTION] ?: DOUBLE_TAP_NONE)
        }
    }

    /** Applies a settings object produced by [exportSettingsJson]. Missing keys are
     *  left untouched; bounded values are clamped to guard hand-edited backups. */
    suspend fun importSettingsJson(obj: JSONObject) {
        context.dataStore.edit { p ->
            if (obj.has("confirm_exit"))           p[Keys.CONFIRM_EXIT] = obj.getBoolean("confirm_exit")
            if (obj.has("lock_timeout_seconds"))   p[Keys.LOCK_TIMEOUT_SECONDS] = obj.getInt("lock_timeout_seconds").coerceAtLeast(0)
            if (obj.has("invert_terminal_scroll")) p[Keys.INVERT_TERMINAL_SCROLL] = obj.getBoolean("invert_terminal_scroll")
            if (obj.has("night_mode"))             p[Keys.NIGHT_MODE] = obj.getInt("night_mode")
            if (obj.has("allow_screenshots"))      p[Keys.ALLOW_SCREENSHOTS] = obj.getBoolean("allow_screenshots")
            if (obj.has("scrollback_lines"))       p[Keys.SCROLLBACK_LINES] = obj.getInt("scrollback_lines").coerceAtLeast(1)
            if (obj.has("terminal_font_size"))     p[Keys.TERMINAL_FONT_SIZE] = obj.getInt("terminal_font_size").coerceIn(MIN_TERMINAL_FONT_SIZE, MAX_TERMINAL_FONT_SIZE)
            if (obj.has("keep_screen_on"))         p[Keys.KEEP_SCREEN_ON] = obj.getBoolean("keep_screen_on")
            if (obj.has("terminal_color_scheme"))  p[Keys.TERMINAL_COLOR_SCHEME] = obj.getInt("terminal_color_scheme").coerceIn(TERMINAL_SCHEME_DARK, TERMINAL_SCHEME_FOLLOW_APP)
            if (obj.has("history_suggestions"))    p[Keys.HISTORY_SUGGESTIONS] = obj.getBoolean("history_suggestions")
            if (obj.has("suggestions_bar_sticky")) p[Keys.SUGGESTIONS_BAR_STICKY] = obj.getBoolean("suggestions_bar_sticky")
            if (obj.has("double_tap_action"))      p[Keys.DOUBLE_TAP_ACTION] = obj.getInt("double_tap_action").coerceIn(DOUBLE_TAP_NONE, DOUBLE_TAP_TAB_TWICE)
        }
    }
}
