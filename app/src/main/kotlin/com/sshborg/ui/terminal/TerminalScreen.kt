package com.sshborg.ui.terminal

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sshborg.terminal.TerminalView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    hostId: Long,
    onBack: () -> Unit,
    vm: TerminalViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val title by vm.title.collectAsState()
    val redrawTick by vm.redrawTick.collectAsState()

    // Wire TerminalView reference so we can call invalidate on redraw ticks
    val terminalViewRef = remember { mutableStateOf<TerminalView?>(null) }

    LaunchedEffect(redrawTick) { terminalViewRef.value?.invalidate() }

    // Mostra la tastiera e dai il focus alla view appena la connessione è pronta
    LaunchedEffect(state) {
        if (state is ConnectionState.Connected) {
            terminalViewRef.value?.showKeyboard()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title.ifEmpty { "Terminal" }, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { vm.disconnect(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // Main terminal view (always rendered so size is known)
            AndroidView(
                factory = { ctx ->
                    TerminalView(ctx).also { view ->
                        view.emulator = vm.emulator
                        view.onInput = { bytes -> vm.sendInput(bytes) }
                        terminalViewRef.value = view
                    }
                },
                update = { view ->
                    view.emulator = vm.emulator
                    view.onInput = { bytes -> vm.sendInput(bytes) }
                    val cols = view.termColumns
                    val rows = view.termRows
                    // Fire connect once we know the real dimensions
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Overlays depending on state
            when (val s = state) {
                is ConnectionState.Connecting -> LoadingOverlay("Connecting…")

                is ConnectionState.PasswordPrompt -> PasswordDialog(
                    hostname = s.hostname,
                    onConfirm = vm::submitPassword,
                    onDismiss = { vm.disconnect(); onBack() },
                )

                is ConnectionState.HostKeyPrompt -> HostKeyDialog(
                    hostname = s.hostname,
                    fingerprint = s.fingerprint,
                    onAccept = { vm.acceptHostKey() },
                    onReject = { vm.rejectHostKey(); onBack() },
                )

                is ConnectionState.Error -> ErrorOverlay(message = s.message, onBack = onBack)

                is ConnectionState.Disconnected -> DisconnectedOverlay(onBack = onBack)

                is ConnectionState.Connected -> { /* normal, no overlay */ }
            }
        }
    }

    // Connect when composition first runs
    LaunchedEffect(hostId) { vm.connect(hostId) }
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
private fun PasswordDialog(hostname: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Password for $hostname") },
        text = {
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(password) }) { Text("Connect") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun HostKeyDialog(hostname: String, fingerprint: String, onAccept: () -> Unit, onReject: () -> Unit) {
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text("Unknown host") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Host: $hostname")
                Text("Fingerprint:")
                Text(fingerprint, style = MaterialTheme.typography.bodySmall)
                Text("Do you trust this host?")
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Trust") } },
        dismissButton = { TextButton(onClick = onReject) { Text("Reject") } },
    )
}

@Composable
private fun ErrorOverlay(message: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Connection failed", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(message, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack) { Text("Back") }
            }
        }
    }
}

@Composable
private fun DisconnectedOverlay(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Disconnected")
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack) { Text("Back") }
            }
        }
    }
}
