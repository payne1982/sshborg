package com.sshborg.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

class AppPreferences(private val context: Context) {

    private object Keys {
        val BIOMETRIC_LOCK           = booleanPreferencesKey("biometric_lock")
        val LOCK_MODE                = intPreferencesKey("lock_mode")
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
        val SFTP_SORT_DIRS_FIRST      = booleanPreferencesKey("sftp_sort_dirs_first")
        val HISTORY_SUGGESTIONS          = booleanPreferencesKey("history_suggestions")
        val SUGGESTIONS_BAR_STICKY       = booleanPreferencesKey("suggestions_bar_sticky")
        val EXTRA_KEYS_BAR_PINNED        = booleanPreferencesKey("extra_keys_bar_pinned")
        val EXTRA_BAR_SELECTED           = stringPreferencesKey("extra_bar_selected")   // ExtraBar id
        val EXTRA_BAR_CUSTOM             = stringPreferencesKey("extra_bar_custom")     // JSON array
        val SECURITY_REMINDER_DISMISSED  = booleanPreferencesKey("security_reminder_dismissed")
        val PRIVACY_POLICY_ACCEPTED      = booleanPreferencesKey("privacy_policy_accepted")
        // In-app lock secret (mode LOCK_SECRET): a salted PBKDF2 hash, never the secret itself.
        val LOCK_SECRET_KIND     = stringPreferencesKey("lock_secret_kind")   // "pin" | "passphrase"
        val LOCK_SECRET_SALT     = stringPreferencesKey("lock_secret_salt")   // base64
        val LOCK_SECRET_HASH     = stringPreferencesKey("lock_secret_hash")   // base64
        val LOCK_SECRET_ITER     = intPreferencesKey("lock_secret_iterations")
        val LOCK_FAILED_ATTEMPTS = intPreferencesKey("lock_failed_attempts")
        val LOCK_LOCKOUT_UNTIL   = longPreferencesKey("lock_lockout_until")   // epoch millis
    }

    /**
     * App-lock mode. Backward compatible with the old boolean [Keys.BIOMETRIC_LOCK]:
     * if the new int key was never written, an old `biometric_lock = true` maps to
     * [LOCK_BIOMETRIC], everything else to [LOCK_NONE]. So users who had the biometric
     * lock on keep it, and nobody who had it off is suddenly locked.
     */
    val lockMode: Flow<Int> =
        context.dataStore.data.map { p ->
            p[Keys.LOCK_MODE] ?: if (p[Keys.BIOMETRIC_LOCK] == true) LOCK_BIOMETRIC else LOCK_NONE
        }

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

    suspend fun setLockMode(mode: Int) {
        context.dataStore.edit { it[Keys.LOCK_MODE] = mode }
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

    /** In the SFTP browser, list folders before files. Default true (folders first). */
    val sftpSortDirsFirst: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.SFTP_SORT_DIRS_FIRST] ?: true }

    suspend fun setSftpSortDirsFirst(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SFTP_SORT_DIRS_FIRST] = enabled }
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

        // App-lock modes (see [lockMode]).
        const val LOCK_NONE = 0       // no lock (default)
        const val LOCK_BIOMETRIC = 1  // biometric only, device credential only if no biometric enrolled
        const val LOCK_DEVICE = 2     // any device screen lock: biometric, PIN, pattern, or password
        const val LOCK_SECRET = 3     // in-app PIN or passphrase (works without device hardware; see AppLockManager)
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

    /**
     * Default "pinned" state of the extra-keys bar: when true the bar stays visible
     * even with the soft keyboard closed. This is only the starting value; the on-bar
     * pin overrides it for the lifetime of a terminal cluster. Default false.
     */
    val extraKeysBarPinned: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.EXTRA_KEYS_BAR_PINNED] ?: false }

    suspend fun setExtraKeysBarPinned(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EXTRA_KEYS_BAR_PINNED] = enabled }
    }

    /** Id of the extra-key bar layout in use (a preset or a custom bar). */
    val extraBarSelectedId: Flow<String> =
        context.dataStore.data.map { it[Keys.EXTRA_BAR_SELECTED] ?: ExtraBarPresets.STANDARD }

    suspend fun setExtraBarSelectedId(id: String) {
        context.dataStore.edit { it[Keys.EXTRA_BAR_SELECTED] = id }
    }

    /** User-defined bars, stored as one JSON array. */
    val customExtraBars: Flow<List<ExtraBar>> =
        context.dataStore.data.map { ExtraBarJson.decodeAll(it[Keys.EXTRA_BAR_CUSTOM]) }

    suspend fun setCustomExtraBars(bars: List<ExtraBar>) {
        context.dataStore.edit { it[Keys.EXTRA_BAR_CUSTOM] = ExtraBarJson.encodeAll(bars) }
    }

    /** Custom bars first, then the presets — the order every picker shows. */
    val allExtraBars: Flow<List<ExtraBar>> =
        customExtraBars.map { it + ExtraBarPresets.all }

    /** The bar to render: the selected one, or the standard preset if it no longer exists. */
    val extraBar: Flow<ExtraBar> =
        combine(extraBarSelectedId, customExtraBars) { id, custom ->
            custom.find { it.id == id } ?: ExtraBarPresets.byId(id) ?: ExtraBarPresets.all.first()
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

    // ── In-app lock secret (PIN / passphrase) ─────────────────────────────────
    // Persisted only as a salted PBKDF2 hash (see AppLockManager). Deliberately NOT
    // part of the settings backup, like lock_mode — a security gate tied to this
    // install, never carried to another device.

    /** A stored lock secret: the hash and the parameters needed to re-derive it. */
    data class LockSecret(val kind: String, val saltB64: String, val hashB64: String, val iterations: Int)

    suspend fun readLockSecret(): LockSecret? {
        val p = context.dataStore.data.first()
        val kind = p[Keys.LOCK_SECRET_KIND] ?: return null
        val salt = p[Keys.LOCK_SECRET_SALT] ?: return null
        val hash = p[Keys.LOCK_SECRET_HASH] ?: return null
        val iter = p[Keys.LOCK_SECRET_ITER] ?: return null
        return LockSecret(kind, salt, hash, iter)
    }

    suspend fun writeLockSecret(secret: LockSecret) {
        context.dataStore.edit {
            it[Keys.LOCK_SECRET_KIND] = secret.kind
            it[Keys.LOCK_SECRET_SALT] = secret.saltB64
            it[Keys.LOCK_SECRET_HASH] = secret.hashB64
            it[Keys.LOCK_SECRET_ITER] = secret.iterations
        }
    }

    /** Removes the stored secret and resets the throttling counters. */
    suspend fun clearLockSecret() {
        context.dataStore.edit {
            it.remove(Keys.LOCK_SECRET_KIND); it.remove(Keys.LOCK_SECRET_SALT)
            it.remove(Keys.LOCK_SECRET_HASH); it.remove(Keys.LOCK_SECRET_ITER)
            it.remove(Keys.LOCK_FAILED_ATTEMPTS); it.remove(Keys.LOCK_LOCKOUT_UNTIL)
        }
    }

    /** (failedAttempts, lockoutUntilEpochMillis). */
    suspend fun readLockAttempts(): Pair<Int, Long> {
        val p = context.dataStore.data.first()
        return (p[Keys.LOCK_FAILED_ATTEMPTS] ?: 0) to (p[Keys.LOCK_LOCKOUT_UNTIL] ?: 0L)
    }

    suspend fun writeLockAttempts(failedAttempts: Int, lockoutUntil: Long) {
        context.dataStore.edit {
            it[Keys.LOCK_FAILED_ATTEMPTS] = failedAttempts
            it[Keys.LOCK_LOCKOUT_UNTIL] = lockoutUntil
        }
    }

    // ── Settings backup ──────────────────────────────────────────────────────
    // Only portable UI/terminal preferences are backed up. Deliberately excluded:
    // lock_mode and keystore_encryption (security gates tied to this device's
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
            put("extra_keys_bar_pinned",  p[Keys.EXTRA_KEYS_BAR_PINNED] ?: false)
            put("double_tap_action",      p[Keys.DOUBLE_TAP_ACTION] ?: DOUBLE_TAP_NONE)
            put("extra_bar_selected",     p[Keys.EXTRA_BAR_SELECTED] ?: ExtraBarPresets.STANDARD)
            put("extra_bar_custom",       org.json.JSONArray(ExtraBarJson.encodeAll(
                ExtraBarJson.decodeAll(p[Keys.EXTRA_BAR_CUSTOM]))))
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
            if (obj.has("extra_keys_bar_pinned"))  p[Keys.EXTRA_KEYS_BAR_PINNED] = obj.getBoolean("extra_keys_bar_pinned")
            if (obj.has("double_tap_action"))      p[Keys.DOUBLE_TAP_ACTION] = obj.getInt("double_tap_action").coerceIn(DOUBLE_TAP_NONE, DOUBLE_TAP_TAB_TWICE)
            // Custom bars replace the local set (they carry their own ids); a selected id that
            // resolves to nothing falls back to the standard preset at read time.
            val customBars = obj.optJSONArray("extra_bar_custom")?.toString()
            if (customBars != null) p[Keys.EXTRA_BAR_CUSTOM] = ExtraBarJson.encodeAll(ExtraBarJson.decodeAll(customBars))
            if (obj.has("extra_bar_selected"))     p[Keys.EXTRA_BAR_SELECTED] = obj.getString("extra_bar_selected")
        }
    }
}
