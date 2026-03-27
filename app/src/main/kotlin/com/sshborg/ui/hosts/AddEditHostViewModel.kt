package com.sshborg.ui.hosts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sshborg.Screen
import com.sshborg.SshBorgApp
import com.sshborg.data.KeystoreManager
import com.sshborg.data.db.HostEntity
import com.sshborg.data.db.SshKeyEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddEditHostViewModel(app: Application) : AndroidViewModel(app) {

    private val sshBorgApp = app as SshBorgApp
    private val hostDao = sshBorgApp.db.hostDao()
    private val keyDao  = sshBorgApp.db.sshKeyDao()
    private val prefs   = sshBorgApp.appPreferences

    val keys: StateFlow<List<SshKeyEntity>> =
        keyDao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Form state
    var label = MutableStateFlow("")
    var hostname = MutableStateFlow("")
    var port = MutableStateFlow("22")
    var username = MutableStateFlow("")
    var password = MutableStateFlow("")
    var useKey = MutableStateFlow(false)
    var selectedKeyId = MutableStateFlow<Long?>(null)
    var agentForwarding = MutableStateFlow(false)
    /** Raw jump-hosts string: "host1:port,host2:port,...". Only relevant when agentForwarding=true. */
    var jumpHosts = MutableStateFlow("")
    /** Newline-separated port-forwarding rules in -L syntax. */
    var portForwardings = MutableStateFlow("")

    private var editingId: Long? = null

    fun loadHost(hostId: Long) {
        if (hostId == Screen.AddEditHost.NEW_ID) return
        viewModelScope.launch {
            val h = hostDao.getById(hostId) ?: return@launch
            editingId = h.id
            label.value = h.label
            hostname.value = h.hostname
            port.value = h.port.toString()
            username.value = h.username
            password.value = when {
                h.encryptedPassword != null ->
                    withContext(Dispatchers.IO) {
                        runCatching { KeystoreManager.decrypt(h.encryptedPassword) }.getOrDefault("")
                    }
                else -> h.password ?: ""
            }
            useKey.value = h.keyId != null
            selectedKeyId.value = h.keyId
            agentForwarding.value = h.agentForwarding
            jumpHosts.value = h.jumpHosts ?: ""
            portForwardings.value = h.portForwardings ?: ""
        }
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val rawPassword = if (!useKey.value) password.value.takeIf { it.isNotEmpty() } else null
        val encEnabled = prefs.keystoreEncryption.first()

        val (plainPwd, encryptedPwd) = when {
            rawPassword == null -> null to null
            encEnabled -> null to withContext(Dispatchers.IO) { KeystoreManager.encrypt(rawPassword) }
            else -> rawPassword to null
        }

        val newJumpHosts = jumpHosts.value.trim().takeIf { it.isNotEmpty() && agentForwarding.value }
        // Preserve cached jump-host keys only when the jump-hosts string is unchanged.
        // If the user edited it, clear so they are re-prompted for the new hops.
        val existing = editingId?.let { hostDao.getById(it) }
        val preservedJumpHostKeys = if (existing?.jumpHosts == newJumpHosts) existing?.jumpHostKeys else null

        val entity = HostEntity(
            id = editingId ?: 0,
            label = label.value.ifBlank { hostname.value },
            hostname = hostname.value.trim(),
            port = port.value.toIntOrNull() ?: 22,
            username = username.value.trim(),
            password = plainPwd,
            encryptedPassword = encryptedPwd,
            keyId = if (useKey.value) selectedKeyId.value else null,
            agentForwarding = agentForwarding.value,
            jumpHosts = newJumpHosts,
            jumpHostKeys = preservedJumpHostKeys,
            portForwardings = portForwardings.value.trim().takeIf { it.isNotEmpty() },
        )
        hostDao.upsert(entity)
        onDone()
    }
}
