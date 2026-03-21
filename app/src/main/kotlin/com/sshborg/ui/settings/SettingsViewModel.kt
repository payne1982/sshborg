package com.sshborg.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
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

    val biometricLock: StateFlow<Boolean> =
        prefs.biometricLock.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val keystoreEncryption: StateFlow<Boolean> =
        prefs.keystoreEncryption.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val confirmExit: StateFlow<Boolean> =
        prefs.confirmExit.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lockTimeoutSeconds: StateFlow<Int> =
        prefs.lockTimeoutSeconds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 60)

    private val _isMigrating = MutableStateFlow(false)
    val isMigrating: StateFlow<Boolean> = _isMigrating

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val error: SharedFlow<String> = _error

    fun setBiometricLock(enabled: Boolean) {
        viewModelScope.launch { prefs.setBiometricLock(enabled) }
    }

    fun setConfirmExit(enabled: Boolean) {
        viewModelScope.launch { prefs.setConfirmExit(enabled) }
    }

    fun setLockTimeoutSeconds(seconds: Int) {
        viewModelScope.launch { prefs.setLockTimeoutSeconds(seconds) }
    }

    /** Encrypts all existing plain-text keys with Android Keystore. */
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
                prefs.setKeystoreEncryption(true)
            }.onFailure { _error.tryEmit(it.message ?: "Encryption failed") }
            _isMigrating.value = false
        }
    }

    /** Decrypts all keys back to plain-text and removes the Keystore key. */
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
                KeystoreManager.deleteKey()
                prefs.setKeystoreEncryption(false)
            }.onFailure { _error.tryEmit(it.message ?: "Decryption failed") }
            _isMigrating.value = false
        }
    }
}
