package com.sshborg.ui.settings

import android.app.Application
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import com.sshborg.R
import androidx.lifecycle.viewModelScope
import com.sshborg.SshBorgApp
import com.sshborg.data.KeystoreManager
import com.sshborg.data.db.HostEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

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

    val allowScreenshots: StateFlow<Boolean> =
        prefs.allowScreenshots.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val scrollbackLines: StateFlow<Int> =
        prefs.scrollbackLines.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2000)

    val terminalFontSize: StateFlow<Int> =
        prefs.terminalFontSize.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000),
            com.sshborg.data.AppPreferences.DEFAULT_TERMINAL_FONT_SIZE,
        )

    val keepScreenOn: StateFlow<Boolean> =
        prefs.keepScreenOn.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val terminalColorScheme: StateFlow<Int> =
        prefs.terminalColorScheme.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000),
            com.sshborg.data.AppPreferences.TERMINAL_SCHEME_DARK,
        )

    val historySuggestions: StateFlow<Boolean> =
        prefs.historySuggestions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val suggestionsBarSticky: StateFlow<Boolean> =
        prefs.suggestionsBarSticky.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lockTimeoutSeconds: StateFlow<Int> =
        prefs.lockTimeoutSeconds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 60)

    private val _isMigrating = MutableStateFlow(false)
    val isMigrating: StateFlow<Boolean> = _isMigrating

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val error: SharedFlow<String> = _error

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message: SharedFlow<String> = _message

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

    fun setAllowScreenshots(enabled: Boolean) {
        viewModelScope.launch { prefs.setAllowScreenshots(enabled) }
    }

    fun setScrollbackLines(lines: Int) {
        viewModelScope.launch { prefs.setScrollbackLines(lines) }
    }

    fun setTerminalFontSize(sp: Int) {
        viewModelScope.launch { prefs.setTerminalFontSize(sp) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { prefs.setKeepScreenOn(enabled) }
    }

    fun setTerminalColorScheme(scheme: Int) {
        viewModelScope.launch { prefs.setTerminalColorScheme(scheme) }
    }

    fun setHistorySuggestions(enabled: Boolean) {
        viewModelScope.launch { prefs.setHistorySuggestions(enabled) }
    }

    fun setSuggestionsBarSticky(enabled: Boolean) {
        viewModelScope.launch { prefs.setSuggestionsBarSticky(enabled) }
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

    fun exportHosts(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val hosts = hostDao.getAllOnce()
                val arr = JSONArray()
                hosts.forEach { h ->
                    arr.put(JSONObject().apply {
                        put("label", h.label)
                        put("hostname", h.hostname)
                        put("port", h.port)
                        put("username", h.username)
                        put("agentForwarding", h.agentForwarding)
                        put("jumpMode", h.jumpMode)
                        put("sftpStartMode", h.sftpStartMode)
                        h.jumpHosts?.let { put("jumpHosts", it) }
                        h.jumpHostIdList?.let { put("jumpHostIdList", it) }
                        h.portForwardings?.let { put("portForwardings", it) }
                        h.sftpStartDir?.let { put("sftpStartDir", it) }
                    })
                }
                val json = JSONObject().apply {
                    put("version", 1)
                    put("exported_at", java.time.Instant.now().toString())
                    put("hosts", arr)
                }.toString(2)
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                    it.write(json.toByteArray(Charsets.UTF_8))
                }
                _message.tryEmit(getApplication<Application>().getString(R.string.backup_export_success, hosts.size))
            }.onFailure {
                _error.tryEmit(it.message ?: getApplication<Application>().getString(R.string.error_unknown))
            }
        }
    }

    fun importHosts(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val jsonText = getApplication<Application>().contentResolver
                    .openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: throw IllegalStateException(getApplication<Application>().getString(R.string.error_unknown))
                val root = JSONObject(jsonText)
                val arr = root.getJSONArray("hosts")
                val toImport = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    HostEntity(
                        id = 0,
                        label = o.getString("label"),
                        hostname = o.getString("hostname"),
                        port = o.optInt("port", 22),
                        username = o.getString("username"),
                        keyId = null,
                        password = null,
                        encryptedPassword = null,
                        knownHostsEntry = null,
                        agentForwarding = o.optBoolean("agentForwarding", false),
                        lastConnected = null,
                        jumpHosts = o.optString("jumpHosts").takeIf { it.isNotEmpty() },
                        jumpHostKeys = null,
                        portForwardings = o.optString("portForwardings").takeIf { it.isNotEmpty() },
                        jumpMode = o.optString("jumpMode", "simple"),
                        jumpHostIdList = o.optString("jumpHostIdList").takeIf { it.isNotEmpty() },
                        sftpStartMode = o.optString("sftpStartMode", "last"),
                        sftpStartDir = o.optString("sftpStartDir").takeIf { it.isNotEmpty() },
                    )
                }
                val existingByLabel = hostDao.getAllOnce().associateBy { it.label }
                var inserted = 0; var updated = 0
                toImport.forEach { host ->
                    val existing = existingByLabel[host.label]
                    if (existing != null) { hostDao.upsert(host.copy(id = existing.id)); updated++ }
                    else { hostDao.upsert(host); inserted++ }
                }
                _message.tryEmit(
                    getApplication<Application>().getString(R.string.backup_import_success, inserted, updated)
                )
            }.onFailure {
                _error.tryEmit(it.message ?: getApplication<Application>().getString(R.string.error_unknown))
            }
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
