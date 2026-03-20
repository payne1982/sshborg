package com.sshborg.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hosts")
data class HostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    /** Null = password auth; non-null = ID of the key to use */
    val keyId: Long? = null,
    val knownHostsEntry: String? = null,
    val agentForwarding: Boolean = false,
    val lastConnected: Long? = null,
    /** Comma-separated jump hosts: "host1:port,host2:port,..." — only used when agentForwarding=true */
    val jumpHosts: String? = null,
    /** Newline-separated known_hosts lines for each jump host, persisted after first connect */
    val jumpHostKeys: String? = null,
)
