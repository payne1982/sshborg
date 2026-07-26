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
    private val groupDao     = sshBorgApp.db.groupDao()

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

    val doubleTapAction: StateFlow<Int> =
        prefs.doubleTapAction.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000),
            com.sshborg.data.AppPreferences.DOUBLE_TAP_NONE,
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

    fun setDoubleTapAction(action: Int) {
        viewModelScope.launch { prefs.setDoubleTapAction(action) }
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
                val groups = groupDao.getAllOnce()
                val groupNameById = groups.associate { it.id to it.name }
                val groupsArr = JSONArray()
                groups.forEach { g ->
                    groupsArr.put(JSONObject().apply {
                        put("name", g.name)
                        put("color", g.color)
                    })
                }
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
                        put("allowLegacyCiphers", h.allowLegacyCiphers)
                        h.jumpHosts?.let { put("jumpHosts", it) }
                        h.jumpHostIdList?.let { put("jumpHostIdList", it) }
                        h.portForwardings?.let { put("portForwardings", it) }
                        h.sftpStartDir?.let { put("sftpStartDir", it) }
                        h.groupId?.let { gid -> groupNameById[gid]?.let { put("group", it) } }
                        h.color?.let { put("color", it) }
                    })
                }
                val json = JSONObject().apply {
                    put("version", 3)
                    put("exported_at", java.time.Instant.now().toString())
                    put("groups", groupsArr)
                    put("hosts", arr)
                    put("settings", prefs.exportSettingsJson())
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

                // Groups (backup version >= 2): upsert by name, keeping existing IDs.
                val groupIdByName = mutableMapOf<String, Long>()
                root.optJSONArray("groups")?.let { groupsArr ->
                    for (i in 0 until groupsArr.length()) {
                        val g = groupsArr.getJSONObject(i)
                        val name = g.getString("name")
                        val color = g.optInt("color", com.sshborg.data.db.GroupEntity.SWATCHES[0])
                        val existing = groupDao.getByName(name)
                        groupIdByName[name] =
                            if (existing != null) {
                                groupDao.upsert(existing.copy(color = color)); existing.id
                            } else {
                                groupDao.upsert(com.sshborg.data.db.GroupEntity(name = name, color = color))
                            }
                    }
                }
                suspend fun resolveGroupId(name: String?): Long? {
                    if (name.isNullOrEmpty()) return null
                    groupIdByName[name]?.let { return it }
                    // Host references a group missing from the backup: recreate it.
                    val existing = groupDao.getByName(name)
                    val id = existing?.id ?: groupDao.upsert(
                        com.sshborg.data.db.GroupEntity(name = name, color = com.sshborg.data.db.GroupEntity.SWATCHES[0])
                    )
                    groupIdByName[name] = id
                    return id
                }

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
                        allowLegacyCiphers = o.optBoolean("allowLegacyCiphers", false),
                        groupId = resolveGroupId(o.optString("group").takeIf { it.isNotEmpty() }),
                        color = if (o.has("color")) o.getInt("color") else null,
                    )
                }
                val existingByLabel = hostDao.getAllOnce().associateBy { it.label }
                var inserted = 0; var updated = 0
                toImport.forEach { host ->
                    val existing = existingByLabel[host.label]
                    if (existing != null) {
                        // Preserve the fields the backup never carries so re-importing over
                        // an existing host doesn't wipe its credentials or saved state:
                        // auth (key/password) and last-connected time always stay.
                        //
                        // Pinned host keys are kept only while the endpoint they were pinned
                        // to is unchanged, mirroring the edit screen: a stored host key is a
                        // TOFU anchor bound to a specific target, so if the import repoints
                        // the host we drop it and re-verify on next connect instead of
                        // carrying a stale pin (which would prompt forever on the main host,
                        // or hard-fail a jump host). knownHostsEntry follows hostname+port;
                        // jumpHostKeys (simple mode) follows the jumpHosts string.
                        val keepHostKey  = existing.hostname == host.hostname && existing.port == host.port
                        val keepJumpKeys = host.jumpMode == "simple" && existing.jumpHosts == host.jumpHosts
                        hostDao.upsert(host.copy(
                            id                = existing.id,
                            keyId             = existing.keyId,
                            password          = existing.password,
                            encryptedPassword = existing.encryptedPassword,
                            knownHostsEntry   = if (keepHostKey) existing.knownHostsEntry else null,
                            jumpHostKeys      = if (keepJumpKeys) existing.jumpHostKeys else null,
                            lastConnected     = existing.lastConnected,
                        ))
                        updated++
                    } else { hostDao.upsert(host); inserted++ }
                }
                // App settings (backup version >= 3): applied reactively via DataStore.
                root.optJSONObject("settings")?.let { prefs.importSettingsJson(it) }

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
