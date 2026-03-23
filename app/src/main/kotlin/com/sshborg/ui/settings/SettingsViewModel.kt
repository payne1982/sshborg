package com.sshborg.ui.settings

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import com.sshborg.R
import androidx.lifecycle.viewModelScope
import com.sshborg.SshBorgApp
import com.sshborg.data.KeystoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val sshBorgApp   = app as SshBorgApp
    private val prefs        = sshBorgApp.appPreferences
    private val keyDao       = sshBorgApp.db.sshKeyDao()
    private val hostDao      = sshBorgApp.db.hostDao()

    val biometricLock: StateFlow<Boolean> =
        prefs.biometricLock.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val keystoreEncryption: StateFlow<Boolean> =
        prefs.keystoreEncryption.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val confirmExit: StateFlow<Boolean> =
        prefs.confirmExit.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val invertTerminalScroll: StateFlow<Boolean> =
        prefs.invertTerminalScroll.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val nightMode: StateFlow<Int> =
        prefs.nightMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    val lockTimeoutSeconds: StateFlow<Int> =
        prefs.lockTimeoutSeconds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 60)

    private val _isMigrating = MutableStateFlow(false)
    val isMigrating: StateFlow<Boolean> = _isMigrating

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val error: SharedFlow<String> = _error

    /** The BCP-47 tag of the currently forced locale, or "" for system default. */
    val currentLocaleTag: String
        get() {
            val locales = AppCompatDelegate.getApplicationLocales()
            return if (locales.isEmpty) "" else locales[0]?.toLanguageTag() ?: ""
        }

    fun setLocale(tag: String) {
        val localeList = if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                         else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun setBiometricLock(enabled: Boolean) {
        viewModelScope.launch { prefs.setBiometricLock(enabled) }
    }

    fun setConfirmExit(enabled: Boolean) {
        viewModelScope.launch { prefs.setConfirmExit(enabled) }
    }

    fun setInvertTerminalScroll(enabled: Boolean) {
        viewModelScope.launch { prefs.setInvertTerminalScroll(enabled) }
    }

    fun setLockTimeoutSeconds(seconds: Int) {
        viewModelScope.launch { prefs.setLockTimeoutSeconds(seconds) }
    }

    fun setNightMode(mode: Int) {
        viewModelScope.launch { prefs.setNightMode(mode) }
    }

    /** Encrypts all existing plain-text SSH keys and host passwords with Android Keystore. */
    fun enableKeystoreEncryption() {
        _isMigrating.value = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                keyDao.getAllOnce().forEach { key ->
                    if (key.encryptedBlob == null && key.privateKeyPem.isNotBlank()) {
                        val blob = KeystoreManager.encrypt(key.privateKeyPem)
                        keyDao.upsert(key.copy(privateKeyPem = "", encryptedBlob = blob))
                    }
                }
                hostDao.getAllOnce().forEach { host ->
                    if (host.encryptedPassword == null && !host.password.isNullOrEmpty()) {
                        val blob = KeystoreManager.encrypt(host.password)
                        hostDao.upsert(host.copy(password = null, encryptedPassword = blob))
                    }
                }
                prefs.setKeystoreEncryption(true)
            }.onFailure { _error.tryEmit(it.message ?: getApplication<Application>().getString(R.string.error_encryption_failed)) }
            _isMigrating.value = false
        }
    }

    /** Decrypts all SSH keys and host passwords back to plain-text and removes the Keystore key. */
    fun disableKeystoreEncryption() {
        _isMigrating.value = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                keyDao.getAllOnce().forEach { key ->
                    if (key.encryptedBlob != null) {
                        val pem = KeystoreManager.decrypt(key.encryptedBlob)
                        keyDao.upsert(key.copy(privateKeyPem = pem, encryptedBlob = null))
                    }
                }
                hostDao.getAllOnce().forEach { host ->
                    if (host.encryptedPassword != null) {
                        val pwd = KeystoreManager.decrypt(host.encryptedPassword)
                        hostDao.upsert(host.copy(password = pwd, encryptedPassword = null))
                    }
                }
                KeystoreManager.deleteKey()
                prefs.setKeystoreEncryption(false)
            }.onFailure { _error.tryEmit(it.message ?: getApplication<Application>().getString(R.string.error_decryption_failed)) }
            _isMigrating.value = false
        }
    }
}
