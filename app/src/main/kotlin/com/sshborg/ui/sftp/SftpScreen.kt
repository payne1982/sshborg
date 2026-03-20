package com.sshborg.ui.sftp

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sshborg.data.ssh.SftpEntry
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SftpScreen(
    hostId: Long,
    onBack: () -> Unit,
    vm: SftpViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(hostId) { vm.connect(hostId) }

    // Non-fatal operation errors shown as snackbar without leaving listing
    LaunchedEffect(Unit) {
        vm.opError.collect { message ->
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
        }
    }

    // Downloaded: show snackbar, then refresh listing
    LaunchedEffect(state) {
        if (state is SftpViewModel.State.Downloaded) {
            val s = state as SftpViewModel.State.Downloaded
            snackbarHostState.showSnackbar(
                "Saved to ${android.os.Environment.DIRECTORY_DOWNLOADS}/SSHBorg/${s.filename}"
            )
            vm.dismissDownloaded()
        }
        if (state is SftpViewModel.State.Uploaded) {
            val s = state as SftpViewModel.State.Uploaded
            snackbarHostState.showSnackbar("Uploaded: ${s.filename}")
            vm.dismissUploaded()
        }
    }

    // Android hardware back: navigate up in dir tree first, then exit
    BackHandler { if (!vm.navigateUp()) onBack() }

    val currentPath = (state as? SftpViewModel.State.Listing)?.path ?: ""
    val atRoot = currentPath == "/" || currentPath.isEmpty()
    val isListing = state is SftpViewModel.State.Listing

    // File picker — opens system file chooser, result forwarded to ViewModel
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { vm.uploadFile(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        currentPath.ifEmpty { "SFTP" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                navigationIcon = {
                    // Always exits to host list — use ".." row or hardware back to navigate up
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to hosts")
                    }
                },
            )
        },
        floatingActionButton = {
            // FAB visible only when browsing, to upload a file to the current directory
            if (isListing) {
                FloatingActionButton(onClick = { filePicker.launch("*/*") }) {
                    Icon(Icons.Default.Upload, contentDescription = "Upload file")
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {

                is SftpViewModel.State.Connecting -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

                is SftpViewModel.State.HostKeyPrompt -> {
                    HostKeyDialog(
                        hostname    = s.hostname,
                        fingerprint = s.fingerprint,
                        onAccept    = vm::acceptHostKey,
                        onReject    = { vm.rejectHostKey(); onBack() },
                    )
                }

                is SftpViewModel.State.PasswordPrompt -> {
                    PasswordDialog(
                        hostname  = s.hostname,
                        onSubmit  = vm::submitPassword,
                        onCancel  = { vm.submitPassword(""); onBack() },
                    )
                }

                is SftpViewModel.State.Listing -> {
                    LazyColumn(Modifier.fillMaxSize()) {
                        // ".." row — go up one level (hidden at root)
                        if (!atRoot) {
                            item(key = "..") {
                                ListItem(
                                    modifier = Modifier.clickable { vm.navigateUp() },
                                    leadingContent = {
                                        Icon(Icons.Default.SubdirectoryArrowLeft, null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    },
                                    headlineContent = { Text("..") },
                                )
                                HorizontalDivider(thickness = 0.5.dp)
                            }
                        }
                        if (s.entries.isEmpty()) {
                            item {
                                Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Empty directory", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        } else {
                            items(s.entries, key = { it.name }) { entry ->
                                SftpEntryItem(
                                    entry   = entry,
                                    onClick = {
                                        if (entry.isDir) vm.navigateTo("${s.path.trimEnd('/')}/${entry.name}")
                                        else vm.downloadFile(entry, s.path)
                                    },
                                )
                            }
                        }
                    }
                }

                is SftpViewModel.State.Downloading -> {
                    TransferProgress(
                        label   = "Downloading ${s.filename}",
                        bytes   = s.bytesReceived,
                        icon    = Icons.Default.Download,
                    )
                }

                is SftpViewModel.State.Downloaded -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

                is SftpViewModel.State.Uploading -> {
                    TransferProgress(
                        label   = "Uploading ${s.filename}",
                        bytes   = s.bytesSent,
                        icon    = Icons.Default.Upload,
                    )
                }

                is SftpViewModel.State.Uploaded -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

                is SftpViewModel.State.Error -> {
                    Column(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.ErrorOutline, null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp))
                        Text(s.message, style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = onBack) { Text("Go back") }
                    }
                }

                SftpViewModel.State.Disconnected -> {
                    Text("Disconnected", Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
private fun BoxScope.TransferProgress(
    label: String,
    bytes: Long,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Column(
        Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (bytes > 0) {
            Text(formatSize(bytes), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SftpEntryItem(entry: SftpEntry, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(
                if (entry.isDir) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = if (entry.isDir) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = {
            Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            if (!entry.isDir) {
                val date = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
                    .format(Date(entry.modTimeSeconds.toLong() * 1000))
                Text("${formatSize(entry.size)}  ·  $date",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        trailingContent = {
            if (!entry.isDir) {
                Icon(Icons.Default.Download, contentDescription = "Download",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp))
            }
        },
    )
    HorizontalDivider(thickness = 0.5.dp)
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1_024               -> "$bytes B"
    bytes < 1_048_576           -> "%.1f KB".format(bytes / 1_024.0)
    bytes < 1_073_741_824       -> "%.1f MB".format(bytes / 1_048_576.0)
    else                        -> "%.2f GB".format(bytes / 1_073_741_824.0)
}

@Composable
private fun HostKeyDialog(
    hostname: String, fingerprint: String,
    onAccept: () -> Unit, onReject: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text("Unknown host") },
        text  = { Text("$hostname\n\n$fingerprint\n\nConnect anyway?") },
        confirmButton = { TextButton(onClick = onAccept) { Text("Connect") } },
        dismissButton = { TextButton(onClick = onReject)  { Text("Cancel")  } },
    )
}

@Composable
private fun PasswordDialog(
    hostname: String,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var pwd by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Password for $hostname") },
        text  = {
            OutlinedTextField(
                value = pwd, onValueChange = { pwd = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            )
        },
        confirmButton = { TextButton(onClick = { onSubmit(pwd) }) { Text("Connect") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
