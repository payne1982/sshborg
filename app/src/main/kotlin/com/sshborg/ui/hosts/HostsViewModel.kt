package com.sshborg.ui.hosts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    fun toggleGroupCollapsed(group: GroupEntity) =
        viewModelScope.launch { groupDao.setCollapsed(group.id, !group.collapsed) }

    fun saveGroup(group: GroupEntity) = viewModelScope.launch { groupDao.upsert(group) }

    /** Deletes the group; its hosts are kept and become ungrouped. */
    fun deleteGroup(group: GroupEntity) = viewModelScope.launch {
        dao.clearGroup(group.id)
        groupDao.delete(group)
    }
}
