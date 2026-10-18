package com.sshborg.service

import com.sshborg.data.ssh.SftpEntry
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
        // Terminal suggestion state — cached here so it survives the ViewModel
        // being recreated when switching between sibling tabs.
        val commandHistory: List<String> = emptyList(),
        val promptPrefix: String = "",
        // SFTP-specific
        val sftpSession: SftpSession? = null,
        val sftpCurrentPath: String = "/",
        val sftpCut: SftpCut? = null,
    )

    /**
     * Entries cut in the SFTP browser, waiting to be pasted somewhere else on the same server.
     *
     * Held on the session rather than in the browser's ViewModel, which is recreated when you
     * step into a terminal tab and back — and deliberately scoped to one session, because a cut
     * is a rename on one server: pasting it onto another host would be a transfer, which SFTP
     * cannot do at all.
     */
    data class SftpCut(val fromDir: String, val entries: List<SftpEntry>)

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

    /**
     * Whether the soft keyboard was up when each terminal session was last on screen, by session
     * id. A session not in here has never been on screen, and a new one opens with the keyboard
     * up — that one moment is the only time anything raises it by itself. From then on the
     * terminal records what the user does with it and restores that on the way back in, so a
     * keyboard put away stays away across tab switches and re-entries.
     *
     * Not part of session state on purpose: it is where the keyboard was, not what the session is,
     * and writing it through [update] would recompose every screen watching the session list.
     */
    private val keyboardUp = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    fun keyboardWasUp(id: String): Boolean = keyboardUp[id] ?: true

    fun setKeyboardUp(id: String, up: Boolean) {
        if (id == frozenKeyboard) return
        keyboardUp[id] = up
    }

    /**
     * The one session whose keyboard memory is sealed: the tab being left, from the tap that
     * leaves it until it is on screen again.
     *
     * Without this the memory cannot be trusted at all. The IME insets belong to the window, not
     * to a screen, and the screen being left stays composed for the length of the tab transition
     * — longer than it takes the arriving tab to open the keyboard. The tab being left therefore
     * watched the arriving tab's keyboard and wrote it down as its own: leave a tab with the
     * keyboard away, arrive somewhere that opens it, and the tab you left is marked "up". Which
     * is exactly the keyboard that then came back, unasked, the next time you returned to it.
     *
     * One slot is enough: only one tab can be being left at a time.
     */
    @Volatile
    private var frozenKeyboard: String? = null

    fun freezeKeyboard(id: String) {
        frozenKeyboard = id
    }

    fun thawKeyboard(id: String) {
        if (frozenKeyboard == id) frozenKeyboard = null
    }

    /**
     * Cluster-scoped override for the extra-keys bar "pin". Null = untouched, so the
     * UI falls back to the settings default. Set to a concrete value when the user taps
     * the on-bar pin; it holds for as long as at least one session is open and resets to
     * null when the last one closes, so a fresh cluster starts from the settings default.
     * Not part of session state on purpose.
     */
    private val _extraBarPinned = MutableStateFlow<Boolean?>(null)
    val extraBarPinned: StateFlow<Boolean?> = _extraBarPinned.asStateFlow()

    fun setExtraBarPinned(pinned: Boolean) {
        _extraBarPinned.value = pinned
    }

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
        keyboardUp.remove(id)
        thawKeyboard(id)
        if (_sessions.value.isEmpty()) _extraBarPinned.value = null
    }

    fun removeAll() {
        _sessions.update { list ->
            list.forEach {
                runCatching { it.shellSession?.disconnect() }
                runCatching { it.sftpSession?.disconnect() }
            }
            emptyList()
        }
        _extraBarPinned.value = null
    }

    fun get(id: String): ActiveSession? = _sessions.value.find { it.id == id }

    fun forHost(hostId: Long, type: SessionType): List<ActiveSession> =
        _sessions.value.filter { it.hostId == hostId && it.type == type }
}
