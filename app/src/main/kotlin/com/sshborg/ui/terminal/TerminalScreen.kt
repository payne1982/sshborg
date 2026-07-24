package com.sshborg.ui.terminal

import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.sshborg.R
import com.sshborg.SshBorgApp
import com.sshborg.data.AppPreferences
import com.sshborg.service.SessionManager
import com.sshborg.terminal.TerminalView
import kotlinx.coroutines.delay

private val ExtraKeyFont = FontFamily(
    Font(R.font.roboto_condensed_regular),
    Font(R.font.roboto_condensed_bold, FontWeight.Bold),
)
private val ArrowKeyFont = FontFamily(
    Font(R.font.jetbrains_mono_regular),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)
@Suppress("UNUSED_VARIABLE")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(
    sessionId: String,
    sessions: List<SessionManager.ActiveSession>,
    onBack: () -> Unit,
    onSwitchSession: (String) -> Unit,
    vm: TerminalViewModel = viewModel(),
) {
    val state    by vm.state.collectAsState()
    val title    by vm.title.collectAsState()
    val emulator by vm.emulatorFlow.collectAsState()

    val app = LocalContext.current.applicationContext as SshBorgApp
    val ctx = LocalContext.current
    var inSelectionMode by remember { mutableStateOf(false) }
    // Held here rather than in the ViewModel: the ViewModel outlives the composition,
    // so a View reference there keeps the Activity alive after the screen is gone.
    var terminalView by remember { mutableStateOf<TerminalView?>(null) }
    val invertScroll  by app.appPreferences.invertTerminalScroll.collectAsState(initial = false)
    val fontSize      by app.appPreferences.terminalFontSize.collectAsState(
        initial = com.sshborg.data.AppPreferences.DEFAULT_TERMINAL_FONT_SIZE
    )
    val keepScreenOn  by app.appPreferences.keepScreenOn.collectAsState(initial = false)
    val terminalScheme by app.appPreferences.terminalColorScheme.collectAsState(
        initial = AppPreferences.TERMINAL_SCHEME_DARK
    )
    val nightMode     by app.appPreferences.nightMode.collectAsState(
        initial = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    )
    // Mirrors SshBorgTheme's darkTheme resolution so "follow app" matches the chrome
    val appDark = when (nightMode) {
        AppCompatDelegate.MODE_NIGHT_YES -> true
        AppCompatDelegate.MODE_NIGHT_NO  -> false
        else -> isSystemInDarkTheme()
    }
    val terminalLight = when (terminalScheme) {
        AppPreferences.TERMINAL_SCHEME_LIGHT      -> true
        AppPreferences.TERMINAL_SCHEME_FOLLOW_APP -> !appDark
        else                                      -> false
    }
    val suggestions   by vm.suggestions.collectAsState()

    // Siblings: other Shell sessions for the same host (for the tab bar)
    val currentSession = sessions.find { it.id == sessionId }
    val siblingShellSessions = if (currentSession != null)
        sessions.filter { it.hostId == currentSession.hostId && it.type == SessionManager.SessionType.Shell }
    else emptyList()

    var ctrlActive by remember { mutableStateOf(false) }
    var altActive  by remember { mutableStateOf(false) }
    var wordMode   by remember { mutableStateOf(false) }

    val sendInput: (ByteArray) -> Unit = { bytes ->
        val out = when {
            ctrlActive && bytes.size == 1 -> {
                ctrlActive = false
                val ch = bytes[0].toInt() and 0xFF
                when (ch) {
                    in 0x40..0x5F -> byteArrayOf((ch - 0x40).toByte())
                    in 0x61..0x7A -> byteArrayOf((ch - 0x60).toByte())
                    else -> bytes
                }
            }
            altActive && bytes.size == 1 -> { altActive = false; byteArrayOf(0x1B, bytes[0]) }
            else -> bytes
        }
        vm.sendInput(out)
    }

    // Reject host key on hardware back during verification
    BackHandler(state is ConnectionState.HostKeyPrompt) { vm.rejectHostKey() }
    // Cancel password prompt on hardware back
    BackHandler(state is ConnectionState.PasswordPrompt) { vm.submitPassword(""); onBack() }

    LaunchedEffect(state) {
        if (state is ConnectionState.Connected) {
            delay(300)
            terminalView?.showKeyboard()
        }
    }

    val imeVisible = WindowInsets.isImeVisible

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title.ifEmpty { currentSession?.hostLabel ?: stringResource(R.string.terminal_title_default) },
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    // Back = send to background (don't disconnect)
                    IconButton(onClick = { vm.background(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    // Disconnect button
                    IconButton(onClick = { vm.disconnect(); onBack() }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.terminal_disconnect_cd))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().imePadding()) {
                // Terminal view
                AndroidView(
                    factory = { factoryCtx ->
                        TerminalView(factoryCtx).also { view ->
                            view.emulator             = vm.emulatorFlow.value
                            view.fontSizeSp            = fontSize.toFloat()
                            view.invertScroll          = invertScroll
                            view.keepScreenOn          = keepScreenOn
                            view.lightScheme           = terminalLight
                            view.onInput              = sendInput
                            view.onResize             = { cols, rows -> vm.resize(cols, rows) }
                            view.onSelectionModeChanged = { active -> inSelectionMode = active }
                            vm.onNeedsRedraw           = { view.postInvalidate() }
                            terminalView               = view
                        }
                    },
                    update = { view ->
                        view.emulator             = emulator
                        view.fontSizeSp            = fontSize.toFloat()
                        view.invertScroll          = invertScroll
                        view.keepScreenOn          = keepScreenOn
                        view.lightScheme           = terminalLight
                        view.onInput              = sendInput
                        view.onResize             = { cols, rows -> vm.resize(cols, rows) }
                        view.onSelectionModeChanged = { active -> inSelectionMode = active }
                        vm.onNeedsRedraw           = { view.postInvalidate() }
                        terminalView               = view
                        view.wordMode              = wordMode
                        view.postInvalidate()
                    },
                    onRelease = {
                        // onNeedsRedraw captures the view, so it has to go too
                        terminalView     = null
                        vm.onNeedsRedraw = null
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )

                // Tab chips — only when there are multiple sessions for this host
                if (siblingShellSessions.size > 1) {
                    SessionTabRow(
                        sessions       = siblingShellSessions,
                        currentId      = sessionId,
                        onSwitch       = onSwitchSession,
                    )
                }

                // History suggestion chips — when keyboard is open and there are suggestions,
                // or always when sticky mode is enabled (to prevent terminal resizing)
                val suggestionsBarSticky by vm.suggestionsBarSticky.collectAsState()
                if (imeVisible && (suggestions.isNotEmpty() || suggestionsBarSticky)) {
                    SuggestionRow(
                        suggestions = suggestions,
                        sticky = suggestionsBarSticky,
                        onSelect = { cmd ->
                            val currentInput = vm.getCurrentInputForCompletion()
                            if (currentInput.isNotEmpty() && cmd.startsWith(currentInput)) {
                                // Complete in place: send only the remaining suffix
                                sendInput(cmd.removePrefix(currentInput).toByteArray(Charsets.UTF_8))
                            } else {
                                // Fallback: clear line and retype full command
                                sendInput(byteArrayOf(0x15))
                                sendInput(cmd.toByteArray(Charsets.UTF_8))
                            }
                        },
                    )
                }

                // Extra key bar — only when soft keyboard is open
                if (imeVisible) {
                    ExtraKeyRow(
                        ctrlActive       = ctrlActive,
                        altActive        = altActive,
                        wordMode         = wordMode,
                        onCtrlToggle     = { ctrlActive = !ctrlActive },
                        onAltToggle      = { altActive  = !altActive  },
                        onWordModeToggle = { wordMode   = !wordMode   },
                        onKey            = { bytes -> sendInput(bytes) },
                        cursorKeys       = { vm.cursorKeyBytes(it) },
                    )
                }
            }

            // Selection action bar — floats at the top of the terminal when in selection mode
            if (inSelectionMode) {
                val strCopied    = stringResource(R.string.action_copied)
                val strSelection = stringResource(R.string.terminal_copy_selection)
                val strAll       = stringResource(R.string.terminal_copy_all)
                SelectionBar(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    labelCopySelection = strSelection,
                    labelCopyAll       = strAll,
                    onCopySelection = {
                        val text = terminalView?.getSelectedText() ?: ""
                        if (text.isNotEmpty()) {
                            val cb = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                            cb.setPrimaryClip(android.content.ClipData.newPlainText("terminal", text))
                            android.widget.Toast.makeText(ctx, strCopied, android.widget.Toast.LENGTH_SHORT).show()
                        }
                        terminalView?.exitSelectionMode()
                    },
                    onCopyAll = {
                        val text = terminalView?.getAllText() ?: ""
                        if (text.isNotEmpty()) {
                            val cb = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                            cb.setPrimaryClip(android.content.ClipData.newPlainText("terminal", text))
                            android.widget.Toast.makeText(ctx, strCopied, android.widget.Toast.LENGTH_SHORT).show()
                        }
                        terminalView?.exitSelectionMode()
                    },
                )
            }

            // State overlays
            when (val s = state) {
                is ConnectionState.Connecting      -> LoadingOverlay(stringResource(R.string.terminal_connecting))
                is ConnectionState.PasswordPrompt  -> PasswordDialog(
                    hostname      = s.hostname,
                    wrongPassword = s.wrongPassword,
                    onConfirm     = vm::submitPassword,
                    onDismiss     = { vm.submitPassword(""); onBack() },
                )
                is ConnectionState.HostKeyPrompt   -> HostKeyDialog(
                    hostname    = s.hostname,
                    fingerprint = s.fingerprint,
                    onAccept    = { vm.acceptHostKey() },
                    onReject    = { vm.rejectHostKey() },
                )
                is ConnectionState.Error           -> ErrorOverlay(message = s.message, onBack = onBack)
                is ConnectionState.Disconnected    -> DisconnectedOverlay(
                    cause   = s.cause,
                    onClose = { vm.disconnect(); onBack() },
                )
                is ConnectionState.Connected       -> { /* normal */ }
            }
        }
    }

    // Attach + connect on first composition
    LaunchedEffect(sessionId) {
        vm.attach(sessionId)
        if (vm.state.value == ConnectionState.Connecting) {
            vm.connect()
        }
    }

    // Auto-navigate back when the remote shell exits cleanly
    LaunchedEffect(Unit) {
        vm.navBack.collect { onBack() }
    }

    // Dismiss keyboard on screen exit — unless we're just switching to a
    // sibling tab, in which case the incoming screen keeps the keyboard up.
    val view = LocalView.current
    DisposableEffect(Unit) {
        onDispose {
            if (app.sessionManager.switchingTab) {
                app.sessionManager.switchingTab = false
            } else {
                val imm = view.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
    }
}

@Composable
private fun SessionTabRow(
    sessions: List<SessionManager.ActiveSession>,
    currentId: String,
    onSwitch: (String) -> Unit,
) {
    // Browser-style tabs: rounded only at the top so each tab reads as a
    // little "page tab" sitting under the terminal. The active tab is filled
    // (surface, matching the terminal) with bold primary text; the inactive
    // ones are flat and dim with a thin outline so they look recessed.
    val tabShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp)
            .padding(top = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        sessions.forEachIndexed { index, session ->
            val selected = session.id == currentId
            Box(
                modifier = Modifier
                    .clip(tabShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .then(
                        if (selected) Modifier
                        else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, tabShape)
                    )
                    .clickable(enabled = !selected) { onSwitch(session.id) }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text       = "#${index + 1}",
                    fontSize   = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color      = if (selected) MaterialTheme.colorScheme.primary
                                 else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(suggestions: List<String>, sticky: Boolean, onSelect: (String) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .then(if (sticky) Modifier.heightIn(min = 40.dp) else Modifier),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(suggestions) { cmd ->
            Box(
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                    .clickable { onSelect(cmd) }
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    cmd,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ExtraKeyRow(
    ctrlActive: Boolean,
    altActive: Boolean,
    wordMode: Boolean,
    onCtrlToggle: () -> Unit,
    onAltToggle: () -> Unit,
    onWordModeToggle: () -> Unit,
    onKey: (ByteArray) -> Unit,
    cursorKeys: (Char) -> ByteArray,
) {
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val pasteContentDesc = stringResource(R.string.terminal_paste_cd)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        ExtraKey("Ctrl", active = ctrlActive, onClick = onCtrlToggle)
        ExtraKey("Alt",  active = altActive,  onClick = onAltToggle)
        Box(
            modifier = Modifier
                .background(
                    if (wordMode) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                    MaterialTheme.shapes.extraSmall,
                )
                .clickable(onClick = onWordModeToggle)
                .padding(horizontal = 8.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Spellcheck,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (wordMode) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.width(4.dp))
        ExtraKey("ESC",  onClick = { onKey(byteArrayOf(0x1B)) })
        ExtraKey("Tab",  onClick = { onKey(byteArrayOf(0x09)) })
        ExtraKey("↑", horizontalPadding = 10.dp, onClick = { onKey(cursorKeys('A')) })
        ExtraKey("↓", horizontalPadding = 10.dp, onClick = { onKey(cursorKeys('B')) })
        ExtraKey("←", horizontalPadding = 10.dp, onClick = { onKey(cursorKeys('D')) })
        ExtraKey("→", horizontalPadding = 10.dp, onClick = { onKey(cursorKeys('C')) })
        ExtraKey("Home", onClick = { onKey("\u001b[H".toByteArray()) })
        ExtraKey("End",  onClick = { onKey("\u001b[F".toByteArray()) })
        ExtraKey("PgUp", onClick = { onKey("\u001b[5~".toByteArray()) })
        ExtraKey("PgDn", onClick = { onKey("\u001b[6~".toByteArray()) })
        ExtraKey("Del",  onClick = { onKey("\u001b[3~".toByteArray()) })
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.extraSmall)
                .clickable {
                    scope.launch {
                        clipboardManager.getClipEntry()?.clipData?.getItemAt(0)?.text?.toString()
                            ?.toByteArray(Charsets.UTF_8)
                            ?.let { onKey(it) }
                    }
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.ContentPaste, contentDescription = pasteContentDesc,
                modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(4.dp))
        ExtraKey("F1",  onClick = { onKey("\u001bOP".toByteArray()) })
        ExtraKey("F2",  onClick = { onKey("\u001bOQ".toByteArray()) })
        ExtraKey("F3",  onClick = { onKey("\u001bOR".toByteArray()) })
        ExtraKey("F4",  onClick = { onKey("\u001bOS".toByteArray()) })
        ExtraKey("F5",  onClick = { onKey("\u001b[15~".toByteArray()) })
        ExtraKey("F6",  onClick = { onKey("\u001b[17~".toByteArray()) })
        ExtraKey("F7",  onClick = { onKey("\u001b[18~".toByteArray()) })
        ExtraKey("F8",  onClick = { onKey("\u001b[19~".toByteArray()) })
        ExtraKey("F9",  onClick = { onKey("\u001b[20~".toByteArray()) })
        ExtraKey("F10", onClick = { onKey("\u001b[21~".toByteArray()) })
        ExtraKey("F11", onClick = { onKey("\u001b[23~".toByteArray()) })
        ExtraKey("F12", onClick = { onKey("\u001b[24~".toByteArray()) })
    }
}

@Composable
private fun ExtraKey(label: String, active: Boolean = false, horizontalPadding: androidx.compose.ui.unit.Dp = 8.dp, onClick: () -> Unit) {
    val bg        = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val textColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .background(bg, MaterialTheme.shapes.extraSmall)
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        val isArrow = label.length == 1 && label[0] in "↑↓←→"
        Text(
            label,
            fontSize = 12.sp,
            fontFamily = if (isArrow) ArrowKeyFont else ExtraKeyFont,
            fontWeight = if (active || isArrow) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            color = textColor,
        )
    }
}

@Composable
private fun LoadingOverlay(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), shape = MaterialTheme.shapes.medium) {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text(message)
            }
        }
    }
}

@Composable
private fun PasswordDialog(
    hostname: String,
    wrongPassword: Boolean = false,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember(wrongPassword) { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.password_dialog_title, hostname)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (wrongPassword) {
                    Text(
                        stringResource(R.string.password_auth_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password_field_label)) },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    isError = wrongPassword,
                    trailingIcon = {
                        TextButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(
                                if (passwordVisible) stringResource(R.string.action_hide)
                                else stringResource(R.string.action_show)
                            )
                        }
                    },
                )
            }
        },
        confirmButton = {
            OutlinedButton(onClick = { onConfirm(password) }) {
                Text(stringResource(R.string.action_connect))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun HostKeyDialog(hostname: String, fingerprint: String, onAccept: () -> Unit, onReject: () -> Unit) {
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text(stringResource(R.string.hostkey_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.hostkey_terminal_host, hostname))
                Text(stringResource(R.string.hostkey_terminal_fingerprint))
                Text(fingerprint, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.hostkey_terminal_trust_question))
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onAccept) { Text(stringResource(R.string.action_trust)) }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onReject,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error)),
            ) { Text(stringResource(R.string.action_reject)) }
        },
    )
}

@Composable
private fun ErrorOverlay(message: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.terminal_connection_failed), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(message, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack) { Text(stringResource(R.string.action_go_back)) }
            }
        }
    }
}

@Composable
private fun SelectionBar(
    labelCopySelection: String,
    labelCopyAll: String,
    onCopySelection: () -> Unit,
    onCopyAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier       = modifier,
        color          = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        shape          = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCopySelection) { Text(labelCopySelection) }
            TextButton(onClick = onCopyAll)       { Text(labelCopyAll) }
        }
    }
}

@Composable
private fun DisconnectedOverlay(cause: String?, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.terminal_disconnected), style = MaterialTheme.typography.titleMedium)
                if (cause != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(cause, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = onClose) { Text(stringResource(R.string.action_close)) }
            }
        }
    }
}
