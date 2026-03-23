package com.sshborg.ui.sftp

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SftpScreen(
    sessionId: String,
    onBack: () -> Unit,
    vm: SftpViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(sessionId) {
        vm.attach(sessionId)
        if (vm.state.value == SftpViewModel.State.Connecting) {
            vm.connect()
        }
    }

    // Non-fatal operation errors shown as snackbar without leaving listing
    LaunchedEffect(Unit) {
        vm.opError.collect { message ->
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
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

    // Hardware back: navigate up in dir tree; at root, disconnect and go back
    BackHandler { if (!vm.navigateUp()) { vm.disconnect(); onBack() } }

    val currentPath = (state as? SftpViewModel.State.Listing)?.path ?: ""
    val atRoot = currentPath == "/" || currentPath.isEmpty()
    val isListing = state is SftpViewModel.State.Listing

    // Dialog states (local UI only — operations go through ViewModel)
    var entryToDelete by remember { mutableStateOf<SftpEntry?>(null) }
    var entryToRename by remember { mutableStateOf<SftpEntry?>(null) }
    var showMkdirDialog by remember { mutableStateOf(false) }
    var pendingConflict by remember { mutableStateOf<SftpViewModel.ConflictData?>(null) }

    LaunchedEffect(Unit) {
        vm.conflictEvent.collect { pendingConflict = it }
    }

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
                    // Back arrow keeps the session alive; use X to disconnect
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to hosts")
                    }
                },
                actions = {
                    if (isListing) {
                        IconButton(onClick = { vm.refreshListing() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                    IconButton(onClick = { vm.disconnect(); onBack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Disconnect")
                    }
                },
            )
        },
        floatingActionButton = {
            if (isListing) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SmallFloatingActionButton(onClick = { showMkdirDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "New folder")
                    }
                    FloatingActionButton(onClick = { filePicker.launch("*/*") }) {
                        Icon(Icons.Default.Upload, contentDescription = "Upload file")
                    }
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
                        hostname      = s.hostname,
                        wrongPassword = s.wrongPassword,
                        onSubmit      = vm::submitPassword,
                        onCancel      = { vm.submitPassword(""); onBack() },
                    )
                }

                is SftpViewModel.State.Listing -> {
                    var isRefreshing by remember { mutableStateOf(false) }
                    LaunchedEffect(s) { isRefreshing = false }
                    val listState = rememberLazyListState()
                    LaunchedEffect(s.path) { listState.scrollToItem(0) }
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { isRefreshing = true; vm.refreshListing() },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
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
                                    entry       = entry,
                                    onClick     = {
                                        if (entry.isDir) vm.navigateTo("${s.path.trimEnd('/')}/${entry.name}")
                                        else vm.downloadFile(entry, s.path)
                                    },
                                    onRename    = { entryToRename = entry },
                                    onDelete    = { entryToDelete = entry },
                                )
                            }
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

    // Delete confirmation dialog
    entryToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Delete ${if (entry.isDir) "folder" else "file"}") },
            text  = { Text("Delete \"${entry.name}\"?\nThis cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteEntry(entry, currentPath)
                    entryToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) { Text("Cancel") }
            },
        )
    }

    // Rename dialog
    entryToRename?.let { entry ->
        var newName by remember(entry) { mutableStateOf(entry.name) }
        AlertDialog(
            onDismissRequest = { entryToRename = null },
            title = { Text("Rename") },
            text  = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank() && newName != entry.name) {
                            vm.renameEntry(entry, currentPath, newName.trim())
                        }
                        entryToRename = null
                    },
                    enabled = newName.isNotBlank(),
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { entryToRename = null }) { Text("Cancel") }
            },
        )
    }

    // Download conflict dialog
    pendingConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = { pendingConflict = null },
            title = { Text("File already exists") },
            text  = { Text("\"${conflict.entry.name}\" already exists in Downloads/SSHBorg/.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { pendingConflict = null }) {
                        Text("Cancel")
                    }
                    TextButton(onClick = { pendingConflict = null; vm.downloadKeepBoth(conflict) }) {
                        Text("Keep both")
                    }
                    TextButton(onClick = { pendingConflict = null; vm.downloadOverwrite(conflict) }) {
                        Text("Overwrite", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
        )
    }

    // New folder dialog
    if (showMkdirDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showMkdirDialog = false; folderName = "" },
            title = { Text("New folder") },
            text  = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (folderName.isNotBlank()) {
                            vm.createDirectory(currentPath, folderName.trim())
                        }
                        showMkdirDialog = false
                        folderName = ""
                    },
                    enabled = folderName.isNotBlank(),
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showMkdirDialog = false; folderName = "" }) { Text("Cancel") }
            },
        )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SftpEntryItem(
    entry: SftpEntry,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        ListItem(
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true },
            ),
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
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                onClick = { menuExpanded = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { menuExpanded = false; onDelete() },
            )
        }
    }
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
    wrongPassword: Boolean = false,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var pwd by remember(wrongPassword) { mutableStateOf("") }
    var pwdVisible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Password for $hostname") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (wrongPassword) {
                    Text(
                        "Authentication failed. Please try again.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedTextField(
                    value = pwd,
                    onValueChange = { pwd = it },
                    label = { Text("Password") },
                    singleLine = true,
                    isError = wrongPassword,
                    visualTransformation = if (pwdVisible) androidx.compose.ui.text.input.VisualTransformation.None
                                           else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                    ),
                    trailingIcon = {
                        TextButton(onClick = { pwdVisible = !pwdVisible }) {
                            Text(if (pwdVisible) "Hide" else "Show")
                        }
                    },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(pwd) }) { Text("Connect") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
