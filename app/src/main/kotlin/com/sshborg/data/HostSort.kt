package com.sshborg.data

import com.sshborg.data.db.GroupEntity
import com.sshborg.data.db.HostEntity

/**
 * Order of the host list (issue #16). Sorting happens here, in memory, rather than in the
 * DAO: the mode is a setting the user flips at runtime, so a single stable re-sort of the
 * list already in hand beats four SQL queries and a flow swap.
 *
 * Both functions take the list **as the DAO returns it** — hosts by label, groups by name,
 * both with SQLite's binary collation — and every sort below is stable, so that alphabetical
 * order survives untouched as the tiebreaker in all modes. That is also why
 * [HOST_SORT_ALPHA] returns the input unchanged instead of re-sorting it: the list then
 * looks exactly as it always has, down to the position of mixed-case labels.
 *
 * Host positions are scoped to a section (the ungrouped block, or one group), so sorting the
 * whole list by position and splitting it afterwards, as the host screen does, is correct:
 * filtering keeps the relative order of each section.
 */
object HostSort {

    /** Groups follow the manual order only; the other modes leave them alphabetical, so the
     *  section headers stay put and only the hosts inside them move. */
    fun sortGroups(groups: List<GroupEntity>, mode: Int): List<GroupEntity> =
        if (mode == AppPreferences.HOST_SORT_MANUAL) groups.sortedBy { it.position ?: Int.MAX_VALUE }
        else groups

    fun sortHosts(hosts: List<HostEntity>, mode: Int): List<HostEntity> = when (mode) {
        // Never-connected hosts have no timestamp: they belong at the bottom, not the top.
        AppPreferences.HOST_SORT_RECENT  -> hosts.sortedByDescending { it.lastConnected ?: Long.MIN_VALUE }
        AppPreferences.HOST_SORT_POPULAR -> hosts.sortedByDescending { it.connectCount }
        AppPreferences.HOST_SORT_MANUAL  -> hosts.sortedBy { it.position ?: Int.MAX_VALUE }
        else                             -> hosts
    }
}
