package com.sshborg.ui.terminal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sshborg.R
import com.sshborg.SshBorgApp
import com.sshborg.service.SessionManager
import com.sshborg.terminal.TerminalView
import kotlinx.coroutines.delay
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
    val invertScroll by app.appPreferences.invertTerminalScroll.collectAsState(initial = false)

    // Siblings: other Shell sessions for the same host (for the tab bar)
    val currentSession = sessions.find { it.id == sessionId }
    val siblingShellSessions = if (currentSession != null)
        sessions.filter { it.hostId == currentSession.hostId && it.type == SessionManager.SessionType.Shell }
    else emptyList()

    var ctrlActive by remember { mutableStateOf(false) }
    var altActive  by remember { mutableStateOf(false) }

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
            vm.terminalViewRef?.showKeyboard()
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
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().imePadding()) {
                // Terminal view
                AndroidView(
                    factory = { ctx ->
                        TerminalView(ctx).also { view ->
                            view.emulator    = vm.emulatorFlow.value
                            view.invertScroll = invertScroll
                            view.onInput     = sendInput
                            view.onResize    = { cols, rows -> vm.resize(cols, rows) }
                            vm.onNeedsRedraw  = { view.postInvalidate() }
                            vm.terminalViewRef = view
                        }
                    },
                    update = { view ->
                        view.emulator     = emulator
                        view.invertScroll = invertScroll
                        view.onInput      = sendInput
                        view.onResize     = { cols, rows -> vm.resize(cols, rows) }
                        vm.onNeedsRedraw   = { view.postInvalidate() }
                        vm.terminalViewRef = view
                        view.postInvalidate()
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

                // Extra key bar — only when soft keyboard is open
                if (imeVisible) {
                    ExtraKeyRow(
                        ctrlActive  = ctrlActive,
                        altActive   = altActive,
                        onCtrlToggle = { ctrlActive = !ctrlActive },
                        onAltToggle  = { altActive  = !altActive  },
                        onKey        = { bytes -> sendInput(bytes) },
                    )
                }
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

    // Dismiss keyboard on screen exit
    val view = LocalView.current
    DisposableEffect(Unit) {
        onDispose {
            val imm = view.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
}

@Composable
private fun SessionTabRow(
    sessions: List<SessionManager.ActiveSession>,
    currentId: String,
    onSwitch: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        sessions.forEachIndexed { index, session ->
            val selected = session.id == currentId
            FilterChip(
                selected  = selected,
                onClick   = { if (!selected) onSwitch(session.id) },
                label     = { Text("#${index + 1}", fontSize = 12.sp) },
                modifier  = Modifier.height(28.dp),
            )
        }
    }
}

@Composable
private fun ExtraKeyRow(
    ctrlActive: Boolean,
    altActive: Boolean,
    onCtrlToggle: () -> Unit,
    onAltToggle: () -> Unit,
    onKey: (ByteArray) -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
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
        Spacer(Modifier.width(4.dp))
        ExtraKey("ESC",  onClick = { onKey(byteArrayOf(0x1B)) })
        ExtraKey("Tab",  onClick = { onKey(byteArrayOf(0x09)) })
        ExtraKey("↑",    onClick = { onKey("\u001b[A".toByteArray()) })
        ExtraKey("↓",    onClick = { onKey("\u001b[B".toByteArray()) })
        ExtraKey("←",    onClick = { onKey("\u001b[D".toByteArray()) })
        ExtraKey("→",    onClick = { onKey("\u001b[C".toByteArray()) })
        ExtraKey("Home", onClick = { onKey("\u001b[H".toByteArray()) })
        ExtraKey("End",  onClick = { onKey("\u001b[F".toByteArray()) })
        ExtraKey("PgUp", onClick = { onKey("\u001b[5~".toByteArray()) })
        ExtraKey("PgDn", onClick = { onKey("\u001b[6~".toByteArray()) })
        ExtraKey("Del",  onClick = { onKey("\u001b[3~".toByteArray()) })
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.extraSmall)
                .clickable {
                    clipboardManager.getText()?.text
                        ?.toByteArray(Charsets.UTF_8)
                        ?.let { onKey(it) }
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.ContentPaste, contentDescription = pasteContentDesc,
                modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
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
private fun ExtraKey(label: String, active: Boolean = false, onClick: () -> Unit) {
    val bg        = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val textColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .background(bg, MaterialTheme.shapes.extraSmall)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1, color = textColor)
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
            TextButton(onClick = { onConfirm(password) }) {
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
            TextButton(onClick = onAccept) { Text(stringResource(R.string.action_trust)) }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text(stringResource(R.string.action_reject)) }
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
