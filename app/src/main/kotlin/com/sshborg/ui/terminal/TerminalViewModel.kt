package com.sshborg.ui.terminal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.sshborg.R
import androidx.lifecycle.viewModelScope
import com.sshborg.BuildConfig
import com.sshborg.SshBorgApp
import com.sshborg.data.db.HostEntity
import com.sshborg.data.db.hasSavedPassword
import com.sshborg.data.ExtraBar
import com.sshborg.data.ExtraBarPresets
import com.sshborg.data.KeystoreManager
import com.sshborg.data.ssh.*
import com.sshborg.data.terminal.MoshTerminalTransport
import com.sshborg.data.terminal.SshTerminalTransport
import com.sshborg.data.terminal.TerminalTransport
import com.sshborg.service.SessionManager
import com.sshborg.service.SshForegroundService
import com.sshborg.data.terminal.ScreenPast
import com.sshborg.terminal.TerminalEmulator
import com.sshborg.terminal.TerminalScreenHistory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream

sealed interface ConnectionState {
    object Connecting : ConnectionState
    object Connected : ConnectionState
    data class HostKeyPrompt(val hostname: String, val fingerprint: String) : ConnectionState
    /**
     * @param banner what the server said before letting anyone in, if it said anything. It is
     *               read before typing or not at all, which is why it comes with the prompt.
     */
    data class PasswordPrompt(
        val hostname: String,
        val wrongPassword: Boolean = false,
        val banner: String? = null,
    ) : ConnectionState
    data class Error(val message: String, val detail: String? = null) : ConnectionState
    /**
     * Mosh was asked for and could not be had. There is no silent fall back to SSH: the switch is
     * per host and explicit, so somebody who asked for mosh and did not get it is owed the reason
     * and the choice. The SSH session is already open and healthy underneath, which is why
     * carrying on over it costs nothing.
     *
     * @param detail what the server said, with the handover line taken out of it; null when the
     *   failure was silence, which has nothing to show.
     */
    data class MoshFailed(
        val reason: MoshUnavailable.Reason,
        val detail: String? = null,
    ) : ConnectionState
    /**
     * @param summary short, human-readable line shown under the title (all non-clean cases).
     * @param detail  full technical cause (exception text + recent JSch log tail), shown
     *                in the expandable, scrollable, copyable section. Null → no details section.
     */
    data class Disconnected(val summary: String? = null, val detail: String? = null) : ConnectionState
}

class TerminalViewModel(app: Application) : AndroidViewModel(app) {

    private val sshBorgApp    = app as SshBorgApp
    private val sessionManager = sshBorgApp.sessionManager
    private val hostDao        = sshBorgApp.db.hostDao()
    private val keyDao         = sshBorgApp.db.sshKeyDao()
    private val prefs          = sshBorgApp.appPreferences

    private var scrollbackLines = 2000
    init {
        viewModelScope.launch {
            scrollbackLines = prefs.scrollbackLines.first()
        }
    }

    private var sessionId: String? = null

    /** Emulator for the current session — initialized in attach(). */
    private val _emulator = MutableStateFlow(TerminalEmulator(80, 24))
    val emulatorFlow: StateFlow<TerminalEmulator> = _emulator.asStateFlow()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
    val state: StateFlow<ConnectionState> = _state

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title

    var onNeedsRedraw: (() -> Unit)? = null

    private var shellSession: ShellSession? = null

    /**
     * Where the terminal's bytes come from. Separate from [shellSession] on purpose: the SSH
     * session carries SFTP, port forwarding, the agent and the shell history, and it stays even
     * when the terminal itself is riding something else.
     */
    private var transport: TerminalTransport? = null
    private var readerJob: Job? = null
    private var connectJob: Job? = null

    private val hostKeyResult  = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    /** True = carry on over SSH, false = leave. See [ConnectionState.MoshFailed]. */
    private val moshFailureResult = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    private val passwordResult = MutableSharedFlow<String>(extraBufferCapacity = 1)

    // ── Command history suggestions ───────────────────────────────────────────

    /** Commands loaded from the server's shell history file, most-recent first. */
    private val _commandHistory = MutableStateFlow<List<String>>(emptyList())

    /** Filtered suggestions for the current partial input. */
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions

    val suggestionsBarSticky: StateFlow<Boolean> =
        prefs.suggestionsBarSticky.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Effective "pinned" state of the extra-keys bar: the cluster-scoped override from
     * [SessionManager] if the user has toggled the on-bar pin, otherwise the settings
     * default. When true the bar stays visible with the keyboard closed.
     */
    val extraBarPinned: StateFlow<Boolean> =
        combine(sessionManager.extraBarPinned, prefs.extraKeysBarPinned) { override, default ->
            override ?: default
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Flips the pin, storing a concrete override for the current terminal cluster. */
    fun toggleExtraBarPinned() {
        sessionManager.setExtraBarPinned(!extraBarPinned.value)
    }

    /** Layout of the extra-key bar (issue #12) and the list offered by its switch menu. */
    val extraBar: StateFlow<ExtraBar> =
        prefs.extraBar.stateIn(viewModelScope, SharingStarted.Eagerly, ExtraBarPresets.all.first())
    val allExtraBars: StateFlow<List<ExtraBar>> =
        prefs.allExtraBars.stateIn(viewModelScope, SharingStarted.Eagerly, ExtraBarPresets.all)

    fun selectExtraBar(id: String) {
        viewModelScope.launch { prefs.setExtraBarSelectedId(id) }
    }

    /** Prompt string detected from first terminal render, used to strip it from the input line. */
    private var promptPrefix = ""
    private var promptDetected = false
    private var promptDetectJob: Job? = null
    /**
     * The ssh line that still has to be typed into a bastion's shell to finish walking a jump
     * chain, and the clock that types it if no prompt is ever recognised.
     *
     * Volatile because it is armed on the connect's own thread and taken on the main one.
     */
    @Volatile private var pendingHopCommand: String? = null
    private var hopCommandJob: Job? = null
    private var suggestionsUpdateJob: Job? = null

    /** Emitted when the remote shell exits cleanly — screen should navigate back automatically. */
    private val _navBack = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navBack: SharedFlow<Unit> = _navBack

    private val _outOfContact = MutableStateFlow(0)

    /**
     * Seconds since the far end was last heard from, 0 while in contact or when the transport
     * cannot tell — see [TerminalTransport.outOfContactSeconds]. The screen shows a line once it
     * passes a threshold, and takes it away by itself when contact returns.
     */
    val outOfContact: StateFlow<Int> = _outOfContact.asStateFlow()

    private var contactJob: Job? = null

    private val _barWouldHideOutput = MutableStateFlow(false)

    /**
     * Whether a title bar lying over the top of this terminal would cover the host's own output
     * rather than rows deliberately left blank for it.
     *
     * The retracting bar is an overlay, and what keeps it from hiding anything is [rowsUnderTitleBar]
     * line feeds written into the emulator before the first byte arrives, so that the login begins
     * below it. That works because under SSH the output is a stream appended at the cursor.
     *
     * Under mosh it does not work and cannot be made to: the first frame homes the cursor and
     * clears the screen before painting at absolute positions, so the line feeds are erased —
     * measured on the wire rather than assumed. The screen is the server's there, and the
     * client is not allowed to edit it. So the bar starts out of the way instead, and a tap still
     * brings it back for as long as it is wanted.
     */
    val barWouldHideOutput: StateFlow<Boolean> = _barWouldHideOutput.asStateFlow()

    /**
     * Attaches this ViewModel to an existing session in [SessionManager].
     * Must be called before [connect]. If the session is already connected, starts reading.
     */
    fun attach(id: String) {
        sessionId = id
        val session = sessionManager.get(id) ?: run {
            _state.value = ConnectionState.Error(getApplication<Application>().getString(R.string.error_session_not_found))
            return
        }

        // Get or create the emulator for this session
        val em = session.emulator ?: TerminalEmulator(80, 24, scrollbackLines).also { newEm ->
            sessionManager.update(id) { it.copy(emulator = newEm) }
        }
        em.onTitleChanged = { t -> _title.value = t }
        _emulator.value = em

        // Asked of the host rather than of the transport, because the bar's own countdown is
        // shorter than a login: by the time there is a transport to ask, it would already have
        // covered the first screenful. Corrected from the transport once there is one, so a
        // session that fell back to SSH is not left with a stale answer.
        viewModelScope.launch {
            _barWouldHideOutput.value = hostDao.getById(session.hostId)?.mosh == true
        }

        // Restore suggestion state cached on the session. When switching tabs the
        // ViewModel is recreated, and attach() (not connect()) runs, so without this
        // the loaded command history and detected prompt would be lost and the
        // suggestion bar would stay empty until a full reconnect.
        _commandHistory.value = session.commandHistory
        if (session.promptPrefix.isNotEmpty()) {
            promptPrefix = session.promptPrefix
            promptDetected = true
        }

        // If already connected, resume reading
        if (session.shellSession != null) {
            shellSession = session.shellSession
            val resumed = session.transport ?: SshTerminalTransport(session.shellSession).also { made ->
                sessionManager.update(id) { it.copy(transport = made) }
            }
            transport = resumed
            if (resumed.isConnected) {
                _state.value = ConnectionState.Connected
                startReading(resumed)
                // Still owed its ssh line: the ViewModel that armed it was thrown away by a tab
                // switch before the prompt it was waiting for came. Here and not up with the rest
                // of the restored state, because the prompt it waits on has been restored by now
                // — so a session whose prompt is already known is typed into at once.
                session.pendingHopCommand?.let { awaitPromptThenType(it) }
            } else {
                // Session dropped while backgrounded: no exception to catch here, so show JSch's
                // own last disconnect reason as the summary (falls back to a generic line).
                _state.value = ConnectionState.Disconnected(
                    summary = com.sshborg.data.ssh.SshDiagnostics.lastDisconnectReason()
                        ?: getApplication<Application>().getString(R.string.terminal_connection_lost),
                    detail = com.sshborg.data.ssh.SshDiagnostics.lastDisconnectDetail(),
                )
                sessionManager.update(id) { it.copy(status = SessionManager.Status.Disconnected) }
            }
        }
        // else: fresh session — maybeConnect() will start it once a real size is known.
        attached = true
        maybeConnect()
    }

    /**
     * Rows at the top of the grid that the retracting title bar lies over, as the screen measures
     * them (0 when the bar is fixed and the terminal starts below it). Used once, at login, by
     * [connect].
     */
    var rowsUnderTitleBar = 0

    /** Set once [attach] has run, so a size callback that races ahead of it doesn't try to connect early. */
    private var attached = false
    /** Last real geometry reported by the view (0 until the first measurement). */
    private var lastCols = 0
    private var lastRows = 0

    /**
     * Called by the view every time it measures its size. For a fresh session this drives the
     * initial connection: we connect only once we have BOTH an attached session and a real
     * measurement, at that measurement, so the login banner is generated and rendered at the
     * correct width from the start instead of being printed at a default 80 cols and then
     * truncated by the later shrink. Later measurements are plain resizes.
     */
    fun onTerminalSize(cols: Int, rows: Int) {
        lastCols = cols; lastRows = rows
        if (shellSession != null) resize(cols, rows) else maybeConnect()
    }

    /** Connects a fresh session as soon as it is both attached and has a real measured size. */
    private fun maybeConnect() {
        if (shellSession != null || connectJob != null || !attached) return
        if (lastCols > 0 && lastRows > 0) connect(lastCols, lastRows)
        // else: no measurement yet — wait for the first onTerminalSize (connectWithDefaultsIfPending covers the never-measured case)
    }

    /** Fallback: if a fresh session is still unconnected (e.g. no measurement arrived), connect it. */
    fun connectWithDefaultsIfPending() {
        if (shellSession != null || connectJob != null || !attached) return
        if (lastCols > 0 && lastRows > 0) connect(lastCols, lastRows) else connect()
    }

    /** Starts the SSH connection for a newly created session. */
    fun connect(columns: Int = 80, rows: Int = 24) {
        val id     = sessionId ?: return
        val hostId = sessionManager.get(id)?.hostId ?: return
        if (connectJob?.isActive == true) return

        // Size the emulator to the real terminal geometry before any bytes arrive, so the
        // login banner is laid out at the right width from the start. This avoids the
        // shrink-and-truncate that happened when we opened at a default 80x24 and only
        // resized after the banner had already been printed.
        synchronized(_emulator.value) { _emulator.value.resize(columns, rows) }

        connectJob = viewModelScope.launch(Dispatchers.IO) {
            val host = hostDao.getById(hostId) ?: run {
                _state.value = ConnectionState.Error(getApplication<Application>().getString(R.string.error_host_not_found)); return@launch
            }
            // Pre-loaded for host-list mode so they can be looked up in onHostKeyVerify, and
            // so the first of them can be recognised as a bastion just below.
            val jumpHostEntities = if (host.jumpMode == "host_list") {
                buildJumpHostEntities(host.jumpHostIdList)
            } else emptyList()

            // Mosh cannot be tunnelled through a jump chain, so a host whose first hop is itself
            // a mosh host is reached from the other end: the session is made *to that bastion*
            // over mosh, and the rest of the way is typed into it as one ssh command. See
            // [BastionHop] for why one command and not a hop at a time.
            //
            // The bastion's own switch is the whole decision, and there is deliberately nothing
            // to tick on this row: a second consent would only be a second way to be puzzled,
            // and a host's mosh switch already means "reach me over mosh" wherever it is read
            // from. A chain whose first hop has mosh off is tunnelled exactly as it always was.
            //
            // Only the terminal works this way. The SFTP tab opens a session of its own through
            // the whole chain exactly as it did before, because that one is SSH and can be
            // tunnelled.
            //
            // Not taken when this host logs in with a saved password. Past the bastion the login
            // is run by the bastion's own ssh, which cannot be handed that password, so the road
            // would end at a prompt in the terminal with a password sitting unused in the
            // database. The chain is tunnelled instead, which is a session that works and is
            // what the row would have had anyway: nothing was promised, because a row with a
            // chain cannot have mosh of its own to promise it. The form refuses the combination
            // outright; this is what happens if one arrives from an edited backup.
            val bastion = jumpHostEntities.firstOrNull()
                ?.takeIf { it.mosh && !host.hasSavedPassword }
            val hopCommand = bastion?.let {
                BastionHop.compose(
                    target  = BastionHop.Endpoint(host.username, host.hostname, host.port),
                    through = jumpHostEntities.drop(1)
                        .map { h -> BastionHop.Endpoint(h.username, h.hostname, h.port) },
                )
            }
            // A name no shell can be trusted with is the one way out of the arrangement: the
            // bastion is given up on and the ordinary chain is walked instead, which is what this
            // host did before any of this existed.
            val via = bastion?.takeIf { hopCommand != null }
            // The machine this session dials and logs in to: the host itself, or its bastion.
            // Everything about the login belongs to that machine — credentials, host key, legacy
            // ciphers, which mosh-server is started — while everything about the destination
            // still belongs to the host: the ssh line, the connection count, the SFTP tab.
            val target = via ?: host

            var auth: SshAuth = buildAuth(target)
            // The person closed the password prompt instead of answering it. The attempt then
            // ends as an authentication failure, which must not be read as one worth retrying.
            var passwordCancelled = false
            // A server has refused a password at some point, so every prompt from here on says
            // so — including one raised on a later attempt, which starts counting again.
            var passwordRefused = false
            // Whether this attempt got as far as asking. Without it, a server that offers no
            // password method at all would fail, be retried, and fail again for ever.
            var passwordAsked = false

            while (true) {
                _state.value = ConnectionState.Connecting
                passwordAsked = false

                // Empty when the bastion walks the chain: there is nothing left for SSH to
                // tunnel, and what was the first hop is the machine being dialled.
                val jumpHosts = if (via != null) emptyList()
                else if (host.jumpMode == "host_list") {
                    jumpHostEntities.mapNotNull { jumpHost ->
                        val jumpAuth = buildJumpAuth(jumpHost) ?: return@mapNotNull null
                        JumpHost(
                            host             = jumpHost.hostname,
                            port             = jumpHost.port,
                            username         = jumpHost.username,
                            knownHostsEntry  = jumpHost.knownHostsEntry,
                            auth             = jumpAuth,
                            hostId           = jumpHost.id,
                        )
                    }
                } else {
                    parseJumpHosts(host.jumpHosts, host.jumpHostKeys)
                }

                val result = runCatching {
                    SshManager.openShell(
                        params = SshConnectionParams(
                            hostname           = target.hostname,
                            port               = target.port,
                            username           = target.username,
                            auth               = auth,
                            // Either row asking for it is enough. The hop that needs the key is
                            // the bastion, and the host that needs it is the one behind it, so
                            // demanding the switch on both would only mean the ssh line failing
                            // for a reason nobody would look for in the other host's form.
                            agentForwarding    = host.agentForwarding || via?.agentForwarding == true,
                            knownHostsEntry    = target.knownHostsEntry,
                            jumpHosts          = jumpHosts,
                            // The destination's rules, on the bastion's session when there is one.
                            // They still reach what they named: a forwarding is resolved by the
                            // far end, and the bastion is on the same side of the door.
                            portForwardings    = parsePortForwardings(host.portForwardings),
                            allowLegacyCiphers = target.allowLegacyCiphers,
                            // Only mosh has a reason to care which family a session is born on,
                            // and only its own hosts carry an answer.
                            addressFamily      = if (target.mosh) target.moshFamily else "auto",
                        ),
                        columns = columns,
                        rows    = rows,
                        // Raised from inside the connect, because the banner only exists there
                        // (see [SshAuth.Interactive]). Blocks the JSch thread while the person
                        // reads and types, exactly as the host-key question below does.
                        onPasswordNeeded = { hostname, banner, retry ->
                            runBlocking {
                                passwordAsked = true
                                if (retry) passwordRefused = true
                                _state.value = ConnectionState.PasswordPrompt(
                                    hostname      = hostname,
                                    wrongPassword = passwordRefused,
                                    banner        = banner,
                                )
                                val pwd = passwordResult.first()
                                if (pwd.isEmpty()) {
                                    passwordCancelled = true
                                    null
                                } else {
                                    _state.value = ConnectionState.Connecting
                                    pwd
                                }
                            }
                        },
                        onHostKeyVerify = { hostname, fingerprint, keyLine ->
                            runBlocking {
                                _state.value = ConnectionState.HostKeyPrompt(hostname, fingerprint)
                                val accepted = hostKeyResult.first()
                                if (accepted) {
                                    _state.value = ConnectionState.Connecting
                                    val current = hostDao.getById(hostId)
                                    if (current != null) {
                                        // Asked of the machine being dialled, which through a
                                        // bastion is the bastion: its key is its own, and goes on
                                        // its own row rather than on the row that named it.
                                        if (hostname == target.hostname) {
                                            val dialled = hostDao.getById(target.id)
                                            if (dialled != null && dialled.knownHostsEntry == null)
                                                hostDao.upsert(dialled.copy(knownHostsEntry = keyLine))
                                        } else if (host.jumpMode == "host_list") {
                                            // Host-list: persist key to the jump host's own entity
                                            val jumpEntity = jumpHostEntities.find { it.hostname == hostname }
                                            if (jumpEntity != null) {
                                                val jCurrent = hostDao.getById(jumpEntity.id)
                                                if (jCurrent != null && jCurrent.knownHostsEntry == null)
                                                    hostDao.upsert(jCurrent.copy(knownHostsEntry = keyLine))
                                            }
                                        } else {
                                            // Simple mode: persist to target's jumpHostKeys blob
                                            val existing = current.jumpHostKeys
                                                ?.lines()?.filter { it.isNotBlank() } ?: emptyList()
                                            if (keyLine !in existing)
                                                hostDao.upsert(current.copy(jumpHostKeys = (existing + keyLine).joinToString("\n")))
                                        }
                                    }
                                }
                                accepted
                            }
                        },
                    )
                }

                val session = result.getOrNull()
                if (session != null) {
                    shellSession = session
                    val em = _emulator.value
                    // Mosh when the machine being dialled asks for it and there is no chain left
                    // for SSH to walk. [target] is what makes that one test instead of two: it is
                    // the host for a direct session and the bastion for a walked one, and either
                    // way it is the row whose switch is being honoured.
                    //
                    // The second half is belt and braces as it always was — the form cannot save
                    // mosh together with a chain of its own — except that it now also catches a
                    // chain which could not be turned into an ssh line and is being tunnelled.
                    val made = (if (target.mosh && jumpHosts.isEmpty()) {
                        moshOrAnswerFor(session, target, em)
                    } else {
                        SshTerminalTransport(session)
                    }) ?: run {
                        // Asked, told why, and the answer was to leave.
                        runCatching { session.disconnect() }
                        withContext(Dispatchers.Main.immediate) {
                            _state.value = ConnectionState.Disconnected()
                        }
                        sessionManager.remove(id)
                        if (sessionManager.sessions.value.isEmpty()) SshForegroundService.stop(getApplication())
                        return@launch
                    }
                    transport = made
                    synchronized(em) {
                        made.resize(em.buffer.columns, em.buffer.rows)
                        // mosh keeps the terminal on its own side and sends us only what it looks
                        // like, so it never forwards `?1049h` and there is no alternate screen
                        // here to go by. Without this a scroll gesture inside tmux went to our
                        // scrollback instead of to tmux — found on a real device. Asked of the
                        // transport rather than remembered, so the two cannot drift apart: a
                        // session that carried on over SSH after mosh failed gets it right too.
                        em.hasAlternateScreen = !made.screenIsSynchronised
                    }
                    _barWouldHideOutput.value = made.screenIsSynchronised
                    if (made is MoshTerminalTransport) watchMoshInDebug(made)
                    withContext(Dispatchers.Main.immediate) {
                        sessionManager.update(id) {
                            it.copy(shellSession = session, transport = made, status = SessionManager.Status.Connected)
                        }
                        _state.value = ConnectionState.Connected
                    }
                    val dialled = hostDao.getById(target.id) ?: target
                    if (dialled.knownHostsEntry == null)
                        hostDao.upsert(dialled.copy(knownHostsEntry = session.hostKeyLine))
                    // Simple mode: new jump keys → target's jumpHostKeys blob
                    if (session.newJumpHostKeyLines.isNotEmpty()) {
                        val current = hostDao.getById(hostId) ?: host
                        val existing = current.jumpHostKeys?.lines()?.filter { it.isNotBlank() } ?: emptyList()
                        val toAdd = session.newJumpHostKeyLines.filter { it !in existing }
                        if (toAdd.isNotEmpty())
                            hostDao.upsert(current.copy(jumpHostKeys = (existing + toAdd).joinToString("\n")))
                    }
                    // Host-list mode: new jump keys → each jump host's own knownHostsEntry
                    for ((jumpHostId, keyLine) in session.newJumpHostKeyUpdates) {
                        val jCurrent = hostDao.getById(jumpHostId) ?: continue
                        if (jCurrent.knownHostsEntry == null)
                            hostDao.upsert(jCurrent.copy(knownHostsEntry = keyLine))
                    }
                    hostDao.recordConnection(hostId, System.currentTimeMillis())
                    // A login that prints two lines leaves both of them under the title bar, and
                    // the screen reads as empty until the bar retracts. So the first prompt starts
                    // below the bar: as many line feeds as the bar covers, into our own grid,
                    // before the first byte of the banner arrives. Nothing is sent to the server
                    // and nothing enters the scrollback — the cursor simply begins further down.
                    //
                    // Here and not in [startReading], which also runs for a session being picked
                    // up again after the app was away: that one already has its output on screen
                    // and must not be nudged.
                    // Not when the screen is the far side's: writing into our own emulator then
                    // puts us out of step with the frames being computed against it, and the bar
                    // is kept out of the way instead (see [barWouldHideOutput]). It was a race as
                    // well as a mistake — whether these landed before the first frame's clear or
                    // after it decided whether the session was merely odd or actually corrupt,
                    // which is why it showed up as "sometimes three blank rows, sometimes not,
                    // and then the line jumps to the top after a few words".
                    val blankRows = if (made.screenIsSynchronised) 0 else {
                        rowsUnderTitleBar.coerceIn(0, (em.buffer.rows - 1).coerceAtLeast(0))
                    }
                    if (blankRows > 0) synchronized(em) { em.process("\n".repeat(blankRows)) }
                    startReading(made)
                    hopCommand?.takeIf { via != null }?.let { awaitPromptThenType(it) }
                    if (prefs.historySuggestions.first()) loadCommandHistory(session)
                    return@launch
                }

                val err = result.exceptionOrNull()
                // Closing the prompt is an answer: leave, and don't ask again.
                if (passwordCancelled) {
                    _state.value = ConnectionState.Disconnected()
                    sessionManager.remove(id)
                    if (sessionManager.sessions.value.isEmpty()) SshForegroundService.stop(getApplication())
                    return@launch
                }
                // A refused password is worth one more attempt, and the person is asked during
                // it — a saved password that no longer works becomes a question, with whatever
                // the server has to say in front of it. Only when this attempt did ask, or had
                // something saved to try: otherwise there is nobody to ask and nothing changes.
                val worthAsking = auth !is SshAuth.Interactive || passwordAsked
                if (isAuthFailure(err) && auth !is SshAuth.PublicKey && worthAsking) {
                    passwordRefused = true
                    auth = SshAuth.Interactive
                } else {
                    val summary = err?.message?.takeIf { it.isNotBlank() }
                        ?: getApplication<Application>().getString(R.string.error_connection_failed)
                    _state.value = ConnectionState.Error(summary, err?.stackTraceToString())
                    sessionManager.remove(id)
                    if (sessionManager.sessions.value.isEmpty()) SshForegroundService.stop(getApplication())
                    return@launch
                }
            }
        }
    }

    private fun isAuthFailure(err: Throwable?): Boolean {
        val msg = err?.message ?: return false
        return msg.contains("Auth fail", ignoreCase = true) ||
               msg.contains("Auth cancel", ignoreCase = true) ||
               msg.contains("USERAUTH", ignoreCase = true) ||
               msg.contains("authentication", ignoreCase = true)
    }

    private suspend fun buildJumpHostEntities(idList: String?): List<HostEntity> {
        if (idList.isNullOrBlank()) return emptyList()
        return idList.split(",").mapNotNull { it.trim().toLongOrNull() }
            .mapNotNull { hostDao.getById(it) }
    }

    /**
     * Public-key auth for [host]. A stored key is always unlocked — an encrypted one is unlocked
     * at import — so nothing has to be asked for here. Null when the host has no key, or its key
     * cannot be read.
     */
    private suspend fun keyAuthFor(host: HostEntity): SshAuth.PublicKey? {
        val key = host.keyId?.let { keyDao.getById(it) } ?: return null
        val pem = KeystoreManager.getPrivateKeyPem(key) ?: return null
        return SshAuth.PublicKey(pem)
    }

    /** Non-interactive auth for jump hosts — password must already be saved. */
    private suspend fun buildJumpAuth(host: HostEntity): SshAuth? {
        val keyAuth = keyAuthFor(host)
        return when {
            keyAuth != null -> keyAuth
            !host.encryptedPassword.isNullOrEmpty() ->
                runCatching { SshAuth.Password(KeystoreManager.decrypt(host.encryptedPassword)) }.getOrNull()
            !host.password.isNullOrEmpty() -> SshAuth.Password(host.password)
            else -> null
        }
    }

    private suspend fun buildAuth(host: HostEntity): SshAuth {
        val keyAuth = keyAuthFor(host)
        return if (keyAuth != null) {
            keyAuth
        } else if (!host.encryptedPassword.isNullOrEmpty()) {
            SshAuth.Password(KeystoreManager.decrypt(host.encryptedPassword))
        } else if (!host.password.isNullOrEmpty()) {
            SshAuth.Password(host.password)
        } else {
            // Nothing saved: the question is put during the connection instead of before it,
            // so the server's banner can be read before anything is typed.
            SshAuth.Interactive
        }
    }

    /**
     * Starts feeding the emulator from [transport], and decides what to do when it ends.
     *
     * The loop itself is three lines; everything that used to make it long — EOF against a late
     * exit status, a session that goes away without an EOF, JSch closing the pipe instead of
     * returning one — belongs to the transport, because every bit of it is specific to SSH. What
     * is left here is what the *terminal* has to do: feed the emulator, ask for a redraw, keep the
     * prompt detection and the suggestions fed, and close the session down when it is over.
     */
    private fun startReading(transport: TerminalTransport) {
        val em = _emulator.value
        // Whatever this transport knows about being out of contact, passed on to the screen. SSH
        // knows nothing, and then the number stays at zero and no line is ever shown.
        contactJob?.cancel()
        _outOfContact.value = 0
        transport.outOfContactSeconds?.let { seconds ->
            contactJob = viewModelScope.launch { seconds.collect { _outOfContact.value = it } }
        }
        em.onSendResponse = { bytes ->
            runCatching { transport.write(bytes) }
        }
        readerJob?.cancel()
        readerJob = viewModelScope.launch(Dispatchers.IO) {
            val end = transport.read { buffer, n ->
                synchronized(em) { em.process(buffer, 0, n) }
                noteEcho()
                onNeedsRedraw?.invoke()
                detectPromptIfNeeded()
                scheduleUpdateSuggestions()
            }

            // Cancelled by background(): the session stays alive and is not ours to end.
            if (!isActive) return@launch
            finishSession(transport, end)
        }
    }

    /**
     * Starts `mosh-server` over the SSH session and hands back the transport that rides it, or
     * asks the person what to do when it cannot be started at all.
     *
     * The SSH session is left open whatever happens — it carries SFTP, port forwarding, the agent
     * and the shell history — so "carry on over SSH" is simply the transport we would have made
     * anyway, with nothing to redial.
     *
     * The size is sent before the handshake, so that the server's very first frame is already the
     * right shape; the caller resizes again, which costs one more action in the same first
     * datagram and keeps the two paths identical from there on.
     *
     * @return the transport to use, or null when the answer was to leave.
     */
    private suspend fun moshOrAnswerFor(
        session: ShellSession,
        host: HostEntity,
        em: TerminalEmulator,
    ): TerminalTransport? {
        // Held so that it can be closed again if this never gets as far as handing it over. Its
        // loop is already running by then, and nothing else knows about it: a screen left during
        // the handshake would otherwise leave a pump answering a server for ever.
        var unowned: MoshTerminalTransport? = null
        val failure = try {
            val handover = session.startMoshServer(
                networkTimeoutSeconds = MOSH_SERVER_TIMEOUT_SECONDS,
                command = host.moshCommand?.takeIf { it.isNotBlank() } ?: "mosh-server",
                ports = host.moshPorts,
            )
            // The sink of last resort takes the emulator and nothing else — in particular not
            // this ViewModel, which a tab switch throws away while the session carries on.
            // The screen's recent pasts, so that a frame the far end computed against a state we
            // have already passed can be used instead of costing a round trip. Every call into it
            // happens on the one thread that applies host bytes, under the emulator's own lock.
            val history = TerminalScreenHistory(em)
            val made = MoshTerminalTransport(
                handover,
                past = object : ScreenPast {
                    override val depth get() = history.depth
                    override fun keep(state: Long) = synchronized(em) { history.keep(state) }
                    override fun restore(state: Long) = synchronized(em) { history.restore(state) }
                    override fun forget() = synchronized(em) { history.forget() }
                    override fun ignoreRepaint(lines: Int) =
                        synchronized(em) { history.ignoreRepaint(lines) }
                },
            ) { buffer, n ->
                synchronized(em) { em.process(buffer, 0, n) }
            }
            unowned = made
            synchronized(em) { made.resize(em.buffer.columns, em.buffer.rows) }
            made.start()
            if (made.awaitFirstContact()) {
                unowned = null
                return made
            }
            // Nothing came back. There is no error from the far end for a path that will not
            // carry UDP — it looks exactly like a server that has not spoken yet — so silence is
            // the whole diagnosis, and the server is told to go away again.
            val where = made.peerDescription
            made.close()
            unowned = null
            // Which port and, above all, which address family: a rule written for IPv4 does
            // nothing for a session SSH made over IPv6, and that is the mistake this failure looks
            // exactly like. Without it the dialog can only say "something is blocking UDP".
            MoshUnavailable(MoshUnavailable.Reason.NoUdpAnswer, where)
        } catch (e: CancellationException) {
            // Before the general catch, which would otherwise turn a screen being left into a
            // dialog nobody is there to answer.
            throw e
        } catch (e: MoshUnavailable) {
            e
        } catch (e: Exception) {
            // The exec channel itself went wrong. Same question, with whatever is known.
            MoshUnavailable(MoshUnavailable.Reason.NoConnectLine, e.message)
        } finally {
            unowned?.let { runCatching { it.close() } }
        }
        withContext(Dispatchers.Main.immediate) {
            _state.value = ConnectionState.MoshFailed(failure.reason, failure.detail)
        }
        if (!moshFailureResult.first()) return null
        withContext(Dispatchers.Main.immediate) { _state.value = ConnectionState.Connecting }
        return SshTerminalTransport(session)
    }

    /**
     * In a debug build, one line in the log every few seconds about how the mosh session is going.
     *
     * The lab can measure a keystroke against a real server from a desktop; it cannot tell us what
     * the same session costs on somebody's tablet, and the first field report was "half a second a
     * character" where the lab said seventeen milliseconds. This is how that gap gets a number
     * instead of a theory: `adb logcat -s SshBorgMosh`. Nothing in release builds, and nothing in
     * the line that is anybody's business but the session's own health.
     */
    private fun watchMoshInDebug(transport: MoshTerminalTransport) {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            while (true) {
                delay(DIAGNOSTIC_INTERVAL_MS)
                if (!transport.isConnected) return@launch
                android.util.Log.i(TAG_MOSH, transport.diagnosticLine())
            }
        }
    }

    /** Takes the session out of [SessionManager] and tells the screen why it ended. */
    private fun finishSession(transport: TerminalTransport, end: TerminalTransport.End) {
        val id = sessionId ?: return
        if (sessionManager.get(id)?.transport !== transport) return
        sessionManager.remove(id)
        if (sessionManager.sessions.value.isEmpty()) {
            SshForegroundService.stop(getApplication())
        }
        when (end) {
            is TerminalTransport.End.CleanExit -> _navBack.tryEmit(Unit)
            is TerminalTransport.End.Lost -> _state.value = ConnectionState.Disconnected(
                summary = end.summary
                    ?: getApplication<Application>().getString(R.string.terminal_connection_lost),
                detail = end.detail,
            )
        }
    }

    /**
     * When the oldest keystroke still waiting for an answer was typed, or 0.
     *
     * Debug builds only, and the reason it exists is that a field report of "half a second a
     * character" and a lab measurement of seventeen milliseconds cannot both be acted on. It is
     * measured for *either* transport on purpose: the useful comparison is the same device, the
     * same network and the same finger, over SSH and over mosh.
     */
    @Volatile
    private var typedAt = 0L

    private fun noteKeystroke() {
        if (BuildConfig.DEBUG && typedAt == 0L) typedAt = System.currentTimeMillis()
    }

    private fun noteEcho() {
        if (!BuildConfig.DEBUG) return
        val typed = typedAt
        if (typed == 0L) return
        typedAt = 0L
        android.util.Log.i(
            // Not the mosh tag: this one is measured for either transport, which is the whole
            // point of it — the comparison is the same device, network and finger.
            TAG_TERMINAL,
            "echo=${System.currentTimeMillis() - typed}ms" +
                if (transport?.screenIsSynchronised == true) " mosh" else " ssh",
        )
    }

    fun sendInput(data: ByteArray) {
        noteKeystroke()
        // Enter (\r or \n) and Ctrl+C immediately clear suggestions — the line is gone.
        if (data.size == 1 && (data[0] == 0x0D.toByte() || data[0] == 0x0A.toByte() || data[0] == 0x03.toByte())) {
            _suggestions.value = emptyList()
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { transport?.write(data) }
        }
    }

    /** Returns the correct escape sequence for a cursor key, respecting DECCKM mode. */
    fun cursorKeyBytes(code: Char): ByteArray {
        val appMode = _emulator.value.applicationCursorKeys
        return if (appMode) "\u001bO$code".toByteArray() else "\u001b[$code".toByteArray()
    }

    fun resize(cols: Int, rows: Int) {
        val em = _emulator.value
        synchronized(em) { em.resize(cols, rows) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { transport?.resize(cols, rows) }
        }
    }

    fun carryOnOverSsh() { moshFailureResult.tryEmit(true) }
    fun leaveAfterMoshFailed() { moshFailureResult.tryEmit(false) }

    fun acceptHostKey() { hostKeyResult.tryEmit(true) }
    fun rejectHostKey() { hostKeyResult.tryEmit(false) }
    fun submitPassword(password: String) { passwordResult.tryEmit(password) }

    /** Stops the reading loop but keeps the session alive in [SessionManager]. */
    fun background() {
        readerJob?.cancel()
        readerJob = null
        contactJob?.cancel()
        contactJob = null
        onNeedsRedraw = null
    }

    /** Fully disconnects and removes the session from [SessionManager]. */
    fun disconnect() {
        val id = sessionId ?: return
        connectJob?.cancel()
        readerJob?.cancel()
        shellSession = null
        transport = null
        sessionManager.remove(id)
        _state.value = ConnectionState.Disconnected()
        if (sessionManager.sessions.value.isEmpty()) {
            SshForegroundService.stop(getApplication())
        }
    }

    // ── History suggestions helpers ───────────────────────────────────────────

    /**
     * Reads the shell history file over an SFTP channel on the terminal's own connection.
     * Tries ~/.bash_history, ~/.zsh_history, and fish history in order.
     * Silently does nothing if the channel or file read fails.
     *
     * It used to open a second SSH connection for this: a whole extra handshake and login
     * (twice through a jump host, a second OTP prompt for keyboard-interactive users), and one
     * that accepted whatever host key it was shown.
     */
    private fun loadCommandHistory(shell: ShellSession) {
        viewModelScope.launch(Dispatchers.IO) {
            val sftp = runCatching { shell.openSftpChannel() }.getOrNull() ?: return@launch
            try {
                val home = runCatching { sftp.home }.getOrNull() ?: return@launch
                val candidates = listOf(
                    "$home/.bash_history",
                    "$home/.zsh_history",
                    "$home/.local/share/fish/fish_history",
                )
                for (path in candidates) {
                    val content = runCatching {
                        val out = ByteArrayOutputStream()
                        sftp.get(path, out)
                        out.toString(Charsets.UTF_8.name())
                    }.getOrNull() ?: continue
                    val commands = parseHistory(path, content)
                    if (commands.isNotEmpty()) {
                        val cmds = commands
                            .reversed()
                            .filter { it.length > 1 }
                            .distinct()
                            .take(100_000)
                        _commandHistory.value = cmds
                        // Cache on the session so a tab switch (new ViewModel) keeps it.
                        sessionId?.let { id -> sessionManager.update(id) { it.copy(commandHistory = cmds) } }
                        break
                    }
                }
            } finally {
                runCatching { sftp.disconnect() }
            }
        }
    }

    private fun parseHistory(path: String, content: String): List<String> {
        val lines = content.lines()
        return when {
            path.endsWith("fish_history") ->
                // fish format: "- cmd: git status\n  when: 12345"
                lines.filter { it.startsWith("- cmd: ") }
                     .map { it.removePrefix("- cmd: ").trim() }
            path.endsWith("zsh_history") ->
                // Extended format: ": 1234567890:0;command" — or plain one-per-line
                lines.map { line ->
                    Regex("""^: \d+:\d+;(.*)$""").matchEntire(line)?.groupValues?.get(1) ?: line
                }.filter { it.isNotBlank() }
            else ->
                // bash_history: one command per line; timestamp lines start with '#'
                lines.filter { it.isNotBlank() && !it.startsWith('#') }
        }
    }

    /**
     * Schedules a prompt detection after a short debounce.
     * Runs only until the prompt is detected for the first time.
     */
    private fun detectPromptIfNeeded() {
        if (promptDetected) return
        promptDetectJob?.cancel()
        promptDetectJob = viewModelScope.launch {
            delay(300)
            val em = _emulator.value
            val lineText = synchronized(em) {
                em.buffer.getRowText(em.buffer.cursorRow).trimEnd()
            }
            if (lineText.isEmpty()) return@launch
            // Match everything up to and including a prompt marker ($, #, %, ❯, >) followed by a space
            val m = Regex("""^(.*?[\$#%❯>])\s""").find(lineText) ?: return@launch
            promptPrefix = m.groupValues[1] + " "
            promptDetected = true
            // Cache on the session so a tab switch (new ViewModel) keeps it.
            sessionId?.let { id -> sessionManager.update(id) { it.copy(promptPrefix = promptPrefix) } }
            // What a bastion's shell was being waited on for.
            typePendingHopCommand()
        }
    }

    /**
     * Types [command] into the session once its shell looks ready for it.
     *
     * Ready means the first prompt the detector recognises — the same signal the suggestion bar
     * waits for — and [HOP_COMMAND_GRACE_MS] is the answer for a prompt it cannot read, since a
     * shell whose PS1 ends in something that regex has never seen would otherwise never be typed
     * into at all.
     *
     * Typing blind after the grace period costs little: authentication is long over by then, so
     * the only thing listening is a shell, and a shell keeps a line that arrives while it is
     * still printing its own greeting. The one thing that must not happen is typing it twice,
     * which is why the line is taken rather than read.
     */
    private fun awaitPromptThenType(command: String) {
        pendingHopCommand = command
        sessionId?.let { id -> sessionManager.update(id) { it.copy(pendingHopCommand = command) } }
        if (promptDetected) {
            typePendingHopCommand()
            return
        }
        hopCommandJob?.cancel()
        hopCommandJob = viewModelScope.launch {
            delay(HOP_COMMAND_GRACE_MS)
            typePendingHopCommand()
        }
    }

    /**
     * Types the pending line, once. Later calls find nothing left to do.
     *
     * Written straight to the transport rather than through [sendInput]: this is not a keystroke,
     * and counting it as one would put a line nobody typed into the echo measurement and clear a
     * suggestion list the person may be looking at.
     */
    private fun typePendingHopCommand() {
        val command = pendingHopCommand ?: return
        pendingHopCommand = null
        sessionId?.let { id -> sessionManager.update(id) { it.copy(pendingHopCommand = null) } }
        hopCommandJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { transport?.write((command + "\n").toByteArray()) }
        }
    }

    /**
     * Returns the portion of the current terminal line that the user is typing,
     * with the shell prompt stripped. Used for suggestion filtering.
     */
    fun getCurrentInputForCompletion(): String {
        if (!promptDetected || promptPrefix.isEmpty()) return ""
        val em = _emulator.value
        val lineText = synchronized(em) {
            em.buffer.getRowText(em.buffer.cursorRow, em.buffer.cursorCol)
        }
        if (lineText.startsWith(promptPrefix)) return lineText.removePrefix(promptPrefix)
        // Prompt changed (e.g. after cd) — heuristic: text after last prompt marker
        // Return "" rather than the raw line to avoid false positives
        val m = Regex("""[\$#%❯>]\s(.*)$""").find(lineText)
        return m?.groupValues?.get(1) ?: ""
    }

    /** Debounced update of the suggestions list based on current terminal input. */
    private fun scheduleUpdateSuggestions() {
        if (!promptDetected) return
        suggestionsUpdateJob?.cancel()
        suggestionsUpdateJob = viewModelScope.launch {
            delay(80)
            val history = _commandHistory.value
            if (history.isEmpty()) return@launch
            val input = getCurrentInputForCompletion()
            _suggestions.value = if (input.length < 2) emptyList()
            else history.filter { it.startsWith(input) && it != input }.take(8)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Stop reading loop — session stays alive in background
        background()
        // If the connection never completed, clean up the dangling session
        val id = sessionId ?: return
        if (sessionManager.get(id)?.status == SessionManager.Status.Connecting) {
            // A connection still being set up has nobody to hand over to. The SSH session only
            // reaches the SessionManager once the transport has been chosen, so until then it is
            // closed here or not at all — which matters for the window in which the mosh failure
            // dialog is waiting for an answer and the login underneath is already live.
            runCatching { shellSession?.disconnect() }
            sessionManager.remove(id)
            if (sessionManager.sessions.value.isEmpty()) {
                SshForegroundService.stop(getApplication())
            }
        }
    }

    private companion object {
        /**
         * How long `mosh-server` is told to wait for us before exiting. Unset it would wait for
         * ever, which mosh itself suggests for a laptop; a phone abandons sessions far more
         * readily, and a server nobody will ever come back to is a shell left running.
         *
         * An hour, which is deliberately more than [MoshTerminalTransport.SILENCE_LIMIT]: the
         * client is always the first of the two to give up, so this number never decides when a
         * session ends — it only decides how long a server the client has already written off
         * stays on the host. If the terminal ever gains mosh's own "contact lost, still trying"
         * line, both numbers grow together.
         */
        const val MOSH_SERVER_TIMEOUT_SECONDS = 3600

        /**
         * How long a bastion's shell is given to show a prompt we can recognise before the ssh
         * line is typed into it anyway. Generous on purpose: a login that prints a long greeting
         * is still a login, and the cost of being early is nil while the cost of never typing it
         * is a session sitting on the wrong machine.
         */
        const val HOP_COMMAND_GRACE_MS = 5_000L

        /** How often a debug build says in the log how the mosh session is going. */
        const val DIAGNOSTIC_INTERVAL_MS = 2_000L

        /**
         * Logcat tags, in the shape the app already uses for `SshDiagnostics`: one for how a mosh
         * session is going, one for what a keystroke costs over whichever transport is carrying
         * it. `adb logcat -s SshBorgMosh` or `-s SshBorgTerminal`, debug builds only.
         */
        const val TAG_MOSH = "SshBorgMosh"
        const val TAG_TERMINAL = "SshBorgTerminal"
    }
}
