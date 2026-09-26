package com.sshborg.data

import com.sshborg.data.db.GroupEntity
import com.sshborg.data.db.HostEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The host list's order (issue #16). Two properties matter and neither is obvious from reading
 * the code: alphabetical order must survive as the tiebreaker in every mode — which is why the
 * sorts are stable and why ALPHA returns the list untouched — and a host that has never been
 * connected belongs at the bottom, not at the top where a null timestamp would put it.
 */
class HostSortTest {

    private fun host(
        label: String,
        lastConnected: Long? = null,
        connectCount: Int = 0,
        position: Int? = null,
    ) = HostEntity(
        label = label, hostname = "h", username = "u",
        lastConnected = lastConnected, connectCount = connectCount, position = position,
    )

    /** As the DAO hands it over: by label. */
    private val byLabel = listOf(
        host("alpha", lastConnected = 300, connectCount = 1, position = 2),
        host("beta", lastConnected = null, connectCount = 9, position = null),
        host("gamma", lastConnected = 900, connectCount = 1, position = 0),
        host("delta", lastConnected = 100, connectCount = 9, position = 1),
    )

    private fun labels(hosts: List<HostEntity>) = hosts.map { it.label }

    @Test fun `alphabetical leaves the list exactly as it came`() {
        assertEquals(byLabel, HostSort.sortHosts(byLabel, AppPreferences.HOST_SORT_ALPHA))
    }

    @Test fun `recent puts the never connected last`() {
        assertEquals(
            listOf("gamma", "alpha", "delta", "beta"),
            labels(HostSort.sortHosts(byLabel, AppPreferences.HOST_SORT_RECENT)),
        )
    }

    @Test fun `most used keeps alphabetical order inside a tie`() {
        assertEquals(
            listOf("beta", "delta", "alpha", "gamma"),
            labels(HostSort.sortHosts(byLabel, AppPreferences.HOST_SORT_POPULAR)),
        )
    }

    @Test fun `manual follows position and parks the unseeded at the end`() {
        assertEquals(
            listOf("gamma", "delta", "alpha", "beta"),
            labels(HostSort.sortHosts(byLabel, AppPreferences.HOST_SORT_MANUAL)),
        )
    }

    @Test fun `groups only move in manual order`() {
        val groups = listOf(
            GroupEntity(name = "work", color = 0, position = 1),
            GroupEntity(name = "zoo", color = 0, position = 0),
        )
        assertEquals(groups, HostSort.sortGroups(groups, AppPreferences.HOST_SORT_RECENT))
        assertEquals(groups, HostSort.sortGroups(groups, AppPreferences.HOST_SORT_ALPHA))
        assertEquals(
            listOf("zoo", "work"),
            HostSort.sortGroups(groups, AppPreferences.HOST_SORT_MANUAL).map { it.name },
        )
    }
}
