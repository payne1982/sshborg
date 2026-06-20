package com.sshborg.service

import com.sshborg.data.ssh.ShellSession
import com.sshborg.data.ssh.SftpSession
import com.sshborg.terminal.TerminalEmulator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class SessionManager {

    enum class SessionType { Shell, Sftp }

    enum class Status { Connecting, Connected, Disconnected, Error }

    data class ActiveSession(
        val id: String,
        val hostId: Long,
        val hostLabel: String,
        val type: SessionType,
        val status: Status = Status.Connecting,
        // Shell-specific
        val shellSession: ShellSession? = null,
        val emulator: TerminalEmulator? = null,
        // SFTP-specific
        val sftpSession: SftpSession? = null,
        val sftpCurrentPath: String = "/",
    )

    private val _sessions = MutableStateFlow<List<ActiveSession>>(emptyList())
    val sessions: StateFlow<List<ActiveSession>> = _sessions.asStateFlow()

    /**
     * Transient flag set while the terminal is switching between sibling tabs.
     * The leaving screen consumes it to skip hiding the soft keyboard, so the
     * keyboard stays up across a tab switch instead of being dismissed by the
     * outgoing screen's onDispose. Not part of session state on purpose.
     */
    @Volatile
    var switchingTab: Boolean = false

    fun create(hostId: Long, hostLabel: String, type: SessionType): String {
        val id = UUID.randomUUID().toString()
        _sessions.update { it + ActiveSession(id, hostId, hostLabel, type) }
        return id
    }

    fun update(id: String, block: (ActiveSession) -> ActiveSession) {
        _sessions.update { list -> list.map { if (it.id == id) block(it) else it } }
    }

    fun remove(id: String) {
        val session = _sessions.value.find { it.id == id }
        runCatching { session?.shellSession?.disconnect() }
        runCatching { session?.sftpSession?.disconnect() }
        _sessions.update { list -> list.filter { it.id != id } }
    }

    fun removeAll() {
        _sessions.update { list ->
            list.forEach {
                runCatching { it.shellSession?.disconnect() }
                runCatching { it.sftpSession?.disconnect() }
            }
            emptyList()
        }
    }

    fun get(id: String): ActiveSession? = _sessions.value.find { it.id == id }

    fun forHost(hostId: Long, type: SessionType): List<ActiveSession> =
        _sessions.value.filter { it.hostId == hostId && it.type == type }
}
