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
    val password: String? = null,
    val encryptedPassword: String? = null,
    val knownHostsEntry: String? = null,
    val agentForwarding: Boolean = false,
    val lastConnected: Long? = null,
    /** Comma-separated jump hosts: "host1:port,host2:port,..." — only used when agentForwarding=true */
    val jumpHosts: String? = null,
    /** Newline-separated known_hosts lines for each jump host, persisted after first connect */
    val jumpHostKeys: String? = null,
    /**
     * Newline-separated local port-forwarding rules in SSH -L syntax:
     * [bindAddr:]localPort:remoteHost:remotePort
     * e.g. "8080:localhost:8080"
     */
    val portForwardings: String? = null,
    /**
     * Jump host mode: "simple" uses the [jumpHosts] text string,
     * "host_list" uses [jumpHostIdList] (comma-separated host entity IDs).
     */
    val jumpMode: String = "simple",
    /**
     * Ordered comma-separated IDs of host entities to use as jump hosts.
     * Only used when [jumpMode] == "host_list".
     */
    val jumpHostIdList: String? = null,
    /**
     * SFTP starting directory mode: "last" = auto-save last visited path,
     * "fixed" = always use [sftpStartDir], "home" = always use server home.
     */
    val sftpStartMode: String = "last",
    /** Persisted last-visited path ("last" mode) or user-specified path ("fixed" mode). */
    val sftpStartDir: String? = null,
)
