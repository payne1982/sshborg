package com.sshborg.ui.hosts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sshborg.SshBorgApp
import com.sshborg.data.db.HostEntity
import com.sshborg.service.SessionManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HostsViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = (app as SshBorgApp).db.hostDao()
    val sessionManager: SessionManager = (app as SshBorgApp).sessionManager

    val hosts: StateFlow<List<HostEntity>> =
        dao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessions: StateFlow<List<SessionManager.ActiveSession>> =
        sessionManager.sessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteHost(host: HostEntity) = viewModelScope.launch { dao.delete(host) }
}
