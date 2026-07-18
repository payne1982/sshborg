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
import kotlinx.coroutines.flow.map

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

    companion object {
        const val DEFAULT_TERMINAL_FONT_SIZE = 13
        const val MIN_TERMINAL_FONT_SIZE = 8
        const val MAX_TERMINAL_FONT_SIZE = 32
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
}
