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
)
