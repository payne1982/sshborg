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
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sshborg.R
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

    val downloadsDir = android.os.Environment.DIRECTORY_DOWNLOADS

    // Downloaded: show snackbar, then refresh listing
    LaunchedEffect(state) {
        if (state is SftpViewModel.State.Downloaded) {
            val s = state as SftpViewModel.State.Downloaded
            snackbarHostState.showSnackbar(
                "Saved to ${vm.downloadFolder}${s.filename}"
            )
            vm.dismissDownloaded()
        }
        if (state is SftpViewModel.State.Uploaded) {
            val s = state as SftpViewModel.State.Uploaded
            val msg = if (s.totalFiles > 1) "Uploaded ${s.totalFiles} files" else "Uploaded: ${s.filename}"
            snackbarHostState.showSnackbar(msg)
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
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> if (uris.isNotEmpty()) vm.uploadFiles(uris) }

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.sftp_back_cd))
                    }
                },
                actions = {
                    if (isListing) {
                        IconButton(onClick = { vm.refreshListing() }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.sftp_refresh_cd))
                        }
                    }
                    IconButton(onClick = { vm.disconnect(); onBack() }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.sftp_disconnect_cd))
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
                        Icon(Icons.Default.CreateNewFolder, contentDescription = stringResource(R.string.sftp_new_folder_cd))
                    }
                    FloatingActionButton(onClick = { filePicker.launch("*/*") }) {
                        Icon(Icons.Default.Upload, contentDescription = stringResource(R.string.sftp_upload_file_cd))
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
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 240.dp),
                    ) {
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
                                    Text(
                                        stringResource(R.string.sftp_empty_directory),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
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
                        label   = stringResource(R.string.sftp_downloading, s.filename),
                        bytes   = s.bytesReceived,
                        icon    = Icons.Default.Download,
                    )
                }

                is SftpViewModel.State.Downloaded -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

                is SftpViewModel.State.Uploading -> {
                    val uploadLabel = buildString {
                        append(stringResource(R.string.sftp_uploading, s.filename))
                        if (s.totalFiles > 1) append(" (${s.fileIndex}/${s.totalFiles})")
                    }
                    TransferProgress(
                        label   = uploadLabel,
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
                        Button(onClick = onBack) { Text(stringResource(R.string.action_go_back)) }
                    }
                }

                SftpViewModel.State.Disconnected -> {
                    Text(stringResource(R.string.sftp_disconnected), Modifier.align(Alignment.Center))
                }
            }
        }
    }

    // Delete confirmation dialog
    entryToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = {
                Text(
                    if (entry.isDir) stringResource(R.string.sftp_delete_folder_title)
                    else stringResource(R.string.sftp_delete_file_title)
                )
            },
            text  = { Text(stringResource(R.string.sftp_delete_message, entry.name)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteEntry(entry, currentPath)
                    entryToDelete = null
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // Rename dialog
    entryToRename?.let { entry ->
        var newName by remember(entry) { mutableStateOf(entry.name) }
        AlertDialog(
            onDismissRequest = { entryToRename = null },
            title = { Text(stringResource(R.string.sftp_rename_title)) },
            text  = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.sftp_rename_new_name)) },
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
                ) { Text(stringResource(R.string.action_rename)) }
            },
            dismissButton = {
                TextButton(onClick = { entryToRename = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // Download conflict dialog
    pendingConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = { pendingConflict = null },
            title = { Text(stringResource(R.string.sftp_conflict_title)) },
            text  = { Text(stringResource(R.string.sftp_conflict_message, conflict.entry.name)) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { pendingConflict = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    TextButton(onClick = { pendingConflict = null; vm.downloadKeepBoth(conflict) }) {
                        Text(stringResource(R.string.action_keep_both))
                    }
                    TextButton(onClick = { pendingConflict = null; vm.downloadOverwrite(conflict) }) {
                        Text(stringResource(R.string.action_overwrite), color = MaterialTheme.colorScheme.error)
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
            title = { Text(stringResource(R.string.sftp_mkdir_title)) },
            text  = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text(stringResource(R.string.sftp_mkdir_folder_name)) },
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
                ) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showMkdirDialog = false; folderName = "" }) {
                    Text(stringResource(R.string.action_cancel))
                }
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
                    if (entry.isDir) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
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
                    Icon(Icons.Default.Download, contentDescription = stringResource(R.string.sftp_download_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp))
                }
            },
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sftp_menu_rename)) },
                leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                onClick = { menuExpanded = false; onRename() },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.sftp_menu_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                },
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
        title = { Text(stringResource(R.string.hostkey_title)) },
        text  = { Text(stringResource(R.string.hostkey_sftp_body, hostname, fingerprint)) },
        confirmButton = {
            TextButton(onClick = onAccept) { Text(stringResource(R.string.action_connect)) }
        },
        dismissButton = {
            TextButton(onClick = onReject)  { Text(stringResource(R.string.action_cancel)) }
        },
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
        title = { Text(stringResource(R.string.password_dialog_title, hostname)) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (wrongPassword) {
                    Text(
                        stringResource(R.string.password_auth_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedTextField(
                    value = pwd,
                    onValueChange = { pwd = it },
                    label = { Text(stringResource(R.string.password_field_label)) },
                    singleLine = true,
                    isError = wrongPassword,
                    visualTransformation = if (pwdVisible) androidx.compose.ui.text.input.VisualTransformation.None
                                           else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                    ),
                    trailingIcon = {
                        TextButton(onClick = { pwdVisible = !pwdVisible }) {
                            Text(
                                if (pwdVisible) stringResource(R.string.action_hide)
                                else stringResource(R.string.action_show)
                            )
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(pwd) }) { Text(stringResource(R.string.action_connect)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
