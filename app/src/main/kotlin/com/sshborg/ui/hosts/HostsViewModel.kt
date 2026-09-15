package com.sshborg.ui.hosts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sshborg.R
import com.sshborg.SshBorgApp
import com.sshborg.data.AppPreferences
import com.sshborg.data.HostSort
import com.sshborg.data.db.GroupEntity
import com.sshborg.data.db.HostEntity
import com.sshborg.service.SessionManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HostsViewModel(app: Application) : AndroidViewModel(app) {

    private val sshBorgApp = app as SshBorgApp
    private val dao = sshBorgApp.db.hostDao()
    private val groupDao = sshBorgApp.db.groupDao()
    val sessionManager: SessionManager = sshBorgApp.sessionManager

    private val prefs = sshBorgApp.appPreferences

    val hostSortMode: StateFlow<Int> = prefs.hostSortMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppPreferences.HOST_SORT_ALPHA)

    // Both lists arrive in DAO order (label / name ascending) and are re-sorted in memory;
    // see HostSort for why the sorting lives there and not in the queries.
    val hosts: StateFlow<List<HostEntity>> =
        combine(dao.getAll(), prefs.hostSortMode) { list, mode -> HostSort.sortHosts(list, mode) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groups: StateFlow<List<GroupEntity>> =
        combine(groupDao.getAll(), prefs.hostSortMode) { list, mode -> HostSort.sortGroups(list, mode) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // While the manual order is in use, give a position to every row that lacks one: the
        // rows already there when the mode is first picked, then any host or group created
        // later. The first seeding follows the order the previous mode was showing, so
        // switching to manual moves nothing; later ones simply append to the section. Writing
        // a position makes the flow emit again, but the second pass finds nothing to do.
        viewModelScope.launch {
            var previousMode = AppPreferences.HOST_SORT_ALPHA
            combine(prefs.hostSortMode, dao.getAll(), groupDao.getAll()) { mode, h, g ->
                Triple(mode, h, g)
            }.collect { (mode, hostList, groupList) ->
                if (mode != AppPreferences.HOST_SORT_MANUAL) { previousMode = mode; return@collect }
                seedHostPositions(HostSort.sortHosts(hostList, previousMode))
                seedGroupPositions(HostSort.sortGroups(groupList, previousMode))
            }
        }
    }

    /** Positions are scoped to a section, so each one gets its own run of numbers. */
    private suspend fun seedHostPositions(hosts: List<HostEntity>) {
        hosts.groupBy { it.groupId }.forEach { (_, section) ->
            var next = (section.mapNotNull { it.position }.maxOrNull() ?: -1) + 1
            section.filter { it.position == null }.forEach { dao.updatePosition(it.id, next++) }
        }
    }

    private suspend fun seedGroupPositions(groups: List<GroupEntity>) {
        var next = (groups.mapNotNull { it.position }.maxOrNull() ?: -1) + 1
        groups.filter { it.position == null }.forEach { groupDao.updatePosition(it.id, next++) }
    }

    /**
     * Moves a host one step up ([delta] -1) or down (+1) within its own section. Crossing into
     * another group would mean changing groupId, which is the host editor's job, so the ends of
     * a section are simply where the move stops.
     */
    fun moveHost(host: HostEntity, delta: Int) = viewModelScope.launch {
        val section = dao.getAllOnce()
            .filter { it.groupId == host.groupId && it.position != null }
            .sortedBy { it.position }
        val i = section.indexOfFirst { it.id == host.id }
        val j = i + delta
        if (i < 0 || j !in section.indices) return@launch
        dao.updatePosition(section[i].id, section[j].position!!)
        dao.updatePosition(section[j].id, section[i].position!!)
    }

    /** Same, for a whole group section. */
    fun moveGroup(group: GroupEntity, delta: Int) = viewModelScope.launch {
        val ordered = groupDao.getAllOnce().filter { it.position != null }.sortedBy { it.position }
        val i = ordered.indexOfFirst { it.id == group.id }
        val j = i + delta
        if (i < 0 || j !in ordered.indices) return@launch
        groupDao.updatePosition(ordered[i].id, ordered[j].position!!)
        groupDao.updatePosition(ordered[j].id, ordered[i].position!!)
    }

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
            // A copy starts unranked and unpositioned: it lands at the end of its section.
            connectCount = 0,
            position = null,
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
