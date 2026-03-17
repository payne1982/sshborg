package com.sshborg.ui.hosts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sshborg.Screen
import com.sshborg.SshBorgApp
import com.sshborg.data.db.HostEntity
import com.sshborg.data.db.SshKeyEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AddEditHostViewModel(app: Application) : AndroidViewModel(app) {

    private val sshBorgApp = app as SshBorgApp
    private val hostDao = sshBorgApp.db.hostDao()
    private val keyDao  = sshBorgApp.db.sshKeyDao()

    val keys: StateFlow<List<SshKeyEntity>> =
        keyDao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Form state
    var label = MutableStateFlow("")
    var hostname = MutableStateFlow("")
    var port = MutableStateFlow("22")
    var username = MutableStateFlow("")
    var useKey = MutableStateFlow(false)
    var selectedKeyId = MutableStateFlow<Long?>(null)
    var agentForwarding = MutableStateFlow(false)

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
            useKey.value = h.keyId != null
            selectedKeyId.value = h.keyId
            agentForwarding.value = h.agentForwarding
        }
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val entity = HostEntity(
            id = editingId ?: 0,
            label = label.value.ifBlank { hostname.value },
            hostname = hostname.value.trim(),
            port = port.value.toIntOrNull() ?: 22,
            username = username.value.trim(),
            keyId = if (useKey.value) selectedKeyId.value else null,
            agentForwarding = agentForwarding.value,
        )
        hostDao.upsert(entity)
        onDone()
    }
}
