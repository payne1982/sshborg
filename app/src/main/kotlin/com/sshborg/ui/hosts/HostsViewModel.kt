package com.sshborg.ui.hosts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sshborg.R
import com.sshborg.SshBorgApp
import com.sshborg.data.db.GroupEntity
import com.sshborg.data.db.HostEntity
import com.sshborg.service.SessionManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HostsViewModel(app: Application) : AndroidViewModel(app) {

    private val sshBorgApp = app as SshBorgApp
    private val dao = sshBorgApp.db.hostDao()
    private val groupDao = sshBorgApp.db.groupDao()
    val sessionManager: SessionManager = sshBorgApp.sessionManager

    val hosts: StateFlow<List<HostEntity>> =
        dao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groups: StateFlow<List<GroupEntity>> =
        groupDao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessions: StateFlow<List<SessionManager.ActiveSession>> =
        sessionManager.sessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val confirmExit: StateFlow<Boolean> =
        sshBorgApp.appPreferences.confirmExit.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun deleteHost(host: HostEntity) = viewModelScope.launch { dao.delete(host) }

    /**
     * Duplicates a host into a new row (fresh id, "(copy)" label, no last-connected timestamp) and
     * hands the new id back so the caller can open it in the editor. Everything else — credentials,
     * key, jump chain, port-forwards — is copied verbatim, since the point (issue #15) is to reuse a
     * host's settings and only tweak a detail like the port or jump host.
     */
    fun cloneHost(host: HostEntity, onCloned: (Long) -> Unit) = viewModelScope.launch {
        val copy = host.copy(
            id = 0,
            label = getApplication<Application>().getString(R.string.host_clone_label, host.label),
            lastConnected = null,
        )
        onCloned(dao.upsert(copy))
    }

    fun toggleGroupCollapsed(group: GroupEntity) =
        viewModelScope.launch { groupDao.setCollapsed(group.id, !group.collapsed) }

    fun saveGroup(group: GroupEntity) = viewModelScope.launch { groupDao.upsert(group) }

    /** Deletes the group; its hosts are kept and become ungrouped. */
    fun deleteGroup(group: GroupEntity) = viewModelScope.launch {
        dao.clearGroup(group.id)
        groupDao.delete(group)
    }
}
