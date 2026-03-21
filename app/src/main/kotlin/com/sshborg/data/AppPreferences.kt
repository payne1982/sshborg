package com.sshborg.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
        val LOCK_TIMEOUT_SECONDS     = intPreferencesKey("lock_timeout_seconds")
        val ROOT_WARNING_ACKNOWLEDGED = booleanPreferencesKey("root_warning_acknowledged")
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
}
