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
    /** Raw jump-hosts string (simple mode): "host1:port,host2:port,...". */
    var jumpHosts = MutableStateFlow("")
    /** Newline-separated port-forwarding rules in -L syntax. */
    var portForwardings = MutableStateFlow("")
    /** Jump host mode: "simple" = text field, "host_list" = select from hosts. */
    var jumpMode = MutableStateFlow("simple")
    /** Ordered list of host IDs selected as jump hops (host-list mode). 0L = not yet selected. */
    var jumpHostIds = MutableStateFlow<List<Long>>(emptyList())

    private val _editingId = MutableStateFlow<Long?>(null)

    /** All hosts except the one being edited, annotated with whether they can be used as jump hosts. */
    data class JumpHostOption(val host: HostEntity, val isSelectable: Boolean)

    val availableJumpHosts: StateFlow<List<JumpHostOption>> = combine(
        hostDao.getAll(),
        _editingId,
    ) { hosts, editingId ->
        hosts
            .filter { it.id != editingId }
            .map { host ->
                JumpHostOption(
                    host        = host,
                    isSelectable = host.keyId != null ||
                                   !host.encryptedPassword.isNullOrEmpty() ||
                                   !host.password.isNullOrEmpty(),
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var editingId: Long? = null

    fun loadHost(hostId: Long) {
        if (hostId == Screen.AddEditHost.NEW_ID) return
        viewModelScope.launch {
            val h = hostDao.getById(hostId) ?: return@launch
            editingId = h.id
            _editingId.value = h.id
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
            jumpMode.value = h.jumpMode
            jumpHostIds.value = h.jumpHostIdList
                ?.split(",")?.mapNotNull { it.trim().toLongOrNull() } ?: emptyList()
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

        val currentMode = jumpMode.value
        val newJumpHostIdList = if (currentMode == "host_list") {
            jumpHostIds.value.filter { it != 0L }.joinToString(",").takeIf { it.isNotEmpty() }
        } else null
        val newJumpHosts = if (currentMode == "simple") {
            jumpHosts.value.trim().takeIf { it.isNotEmpty() }
        } else null

        // Preserve cached jump-host keys only when the jump-hosts string is unchanged (simple mode).
        val existing = editingId?.let { hostDao.getById(it) }
        val preservedJumpHostKeys = if (currentMode == "simple" && existing?.jumpHosts == newJumpHosts) {
            existing?.jumpHostKeys
        } else null

        val entity = HostEntity(
            id               = editingId ?: 0,
            label            = label.value.ifBlank { hostname.value },
            hostname         = hostname.value.trim(),
            port             = port.value.toIntOrNull() ?: 22,
            username         = username.value.trim(),
            password         = plainPwd,
            encryptedPassword = encryptedPwd,
            keyId            = if (useKey.value) selectedKeyId.value else null,
            agentForwarding  = agentForwarding.value,
            jumpHosts        = newJumpHosts,
            jumpHostKeys     = preservedJumpHostKeys,
            portForwardings  = portForwardings.value.trim().takeIf { it.isNotEmpty() },
            jumpMode         = currentMode,
            jumpHostIdList   = newJumpHostIdList,
        )
        hostDao.upsert(entity)
        onDone()
    }
}
