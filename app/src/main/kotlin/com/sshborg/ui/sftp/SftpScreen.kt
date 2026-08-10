package com.sshborg.ui.sftp

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sshborg.R
import com.sshborg.data.ssh.SftpEntry
import com.sshborg.service.BackgroundTransfer
import com.sshborg.ui.common.ProblemContent
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
    val backgroundTransfers by vm.backgroundTransfers.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectionMode by remember { mutableStateOf(false) }
    var selectedEntries by remember { mutableStateOf(setOf<SftpEntry>()) }

    LaunchedEffect(sessionId) {
        vm.attach(sessionId)
        if (vm.state.value == SftpViewModel.State.Connecting) {
            vm.connect()
        }
    }

    // Non-fatal operation errors shown as snackbar without leaving listing
    val unknownError = stringResource(R.string.error_unknown)
    LaunchedEffect(Unit) {
        vm.opError.collect { message ->
            val display = message.takeIf { it.isNotBlank() } ?: unknownError
            snackbarHostState.showSnackbar(display, duration = SnackbarDuration.Short)
        }
    }

    // Uploaded: refresh listing immediately, show snackbar concurrently.
    // (Foreground downloads now show a persistent completion screen with a Done button —
    //  see the State.Downloaded branch below — so they are not handled here.)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val uploadedMsg = (state as? SftpViewModel.State.Uploaded)?.let { s ->
        if (s.totalFiles > 1) stringResource(R.string.sftp_uploaded_n_files, s.totalFiles)
        else stringResource(R.string.sftp_uploaded, s.filename)
    }
    LaunchedEffect(state) {
        if (state is SftpViewModel.State.Uploaded) {
            uploadedMsg?.let { scope.launch { snackbarHostState.showSnackbar(it) } }
            vm.dismissUploaded()
        }
    }

    val currentPath = (state as? SftpViewModel.State.Listing)?.path ?: ""

    // Clear selection when navigating to a different directory
    LaunchedEffect(currentPath) {
        if (selectionMode) {
            selectionMode = false
            selectedEntries = emptySet()
        }
    }

    val atRoot    = currentPath == "/" || currentPath.isEmpty()
    val isListing = state is SftpViewModel.State.Listing

    // Hardware back: exit selection mode first; then navigate up; at root, disconnect and go back
    BackHandler {
        when {
            selectionMode -> { selectionMode = false; selectedEntries = emptySet() }
            !vm.navigateUp() -> { vm.disconnect(); onBack() }
        }
    }

    // Dialog states (local UI only — operations go through ViewModel)
    var pendingBulkDelete by remember { mutableStateOf<List<SftpEntry>?>(null) }
    var entryToDelete    by remember { mutableStateOf<SftpEntry?>(null) }
    var entryToRename    by remember { mutableStateOf<SftpEntry?>(null) }
    var showMkdirDialog  by remember { mutableStateOf(false) }
    var pendingConflict  by remember { mutableStateOf<SftpViewModel.ConflictData?>(null) }
    var pendingBatchConflict by remember { mutableStateOf<SftpViewModel.BatchConflictData?>(null) }

    LaunchedEffect(Unit) {
        vm.conflictEvent.collect { pendingConflict = it }
    }
    LaunchedEffect(Unit) {
        vm.batchConflictEvent.collect { pendingBatchConflict = it }
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
                    if (selectionMode) {
                        Text(stringResource(R.string.sftp_n_selected, selectedEntries.size))
                    } else {
                        Text(
                            currentPath.ifEmpty { "SFTP" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectionMode) { selectionMode = false; selectedEntries = emptySet() }
                        else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.sftp_back_cd))
                    }
                },
                actions = {
                    if (isListing) {
                        if (selectionMode) {
                            IconButton(
                                onClick = {
                                    vm.downloadEntries(selectedEntries.toList(), currentPath)
                                    selectionMode = false
                                    selectedEntries = emptySet()
                                },
                                enabled = selectedEntries.isNotEmpty(),
                            ) {
                                Icon(Icons.Default.Download, stringResource(R.string.sftp_download_selected_cd))
                            }
                            IconButton(
                                onClick = { pendingBulkDelete = selectedEntries.toList() },
                                enabled = selectedEntries.isNotEmpty(),
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    stringResource(R.string.sftp_delete_selected_cd),
                                    tint = if (selectedEntries.isNotEmpty()) MaterialTheme.colorScheme.error
                                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                )
                            }
                        } else {
                            IconButton(onClick = { selectionMode = true }) {
                                Icon(Icons.Default.CheckBox, stringResource(R.string.sftp_select_items_cd))
                            }
                            IconButton(onClick = { vm.refreshListing() }) {
                                Icon(Icons.Default.Refresh, stringResource(R.string.sftp_refresh_cd))
                            }
                        }
                    }
                    if (!selectionMode) {
                        IconButton(onClick = { vm.disconnect(); onBack() }) {
                            Icon(Icons.Default.Close, stringResource(R.string.sftp_disconnect_cd))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (isListing && !selectionMode) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SmallFloatingActionButton(onClick = { showMkdirDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, stringResource(R.string.sftp_new_folder_cd))
                    }
                    FloatingActionButton(onClick = { filePicker.launch("*/*") }) {
                        Icon(Icons.Default.Upload, stringResource(R.string.sftp_upload_file_cd))
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

                is SftpViewModel.State.Preparing -> {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.sftp_preparing))
                        TextButton(onClick = { vm.cancelDownload() }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
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
                            // ".." row — go up one level (hidden at root, not selectable)
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
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    }
                                }
                            } else {
                                items(s.entries, key = { it.name }) { entry ->
                                    SftpEntryItem(
                                        entry          = entry,
                                        selectionMode  = selectionMode,
                                        isSelected     = entry in selectedEntries,
                                        onClick        = {
                                            if (selectionMode) {
                                                selectedEntries = if (entry in selectedEntries)
                                                    selectedEntries - entry
                                                else
                                                    selectedEntries + entry
                                            } else {
                                                if (entry.isDir) vm.navigateTo("${s.path.trimEnd('/')}/${entry.name}")
                                                else vm.downloadFile(entry, s.path)
                                            }
                                        },
                                        onDownloadFolder = { vm.downloadEntries(listOf(entry), s.path) },
                                        onDownloadInBackground = if (!entry.isDir) {
                                            { vm.downloadFileInBackground(entry, s.path) }
                                        } else null,
                                        onRename       = { entryToRename = entry },
                                        onDelete       = { entryToDelete = entry },
                                    )
                                }
                            }
                        }
                    }
                }

                is SftpViewModel.State.Deleting -> {
                    TransferProgress(
                        label = buildString {
                            append(stringResource(R.string.sftp_deleting_label))
                            if (s.total > 1) append(" (${s.index}/${s.total})")
                        },
                        sublabel = s.name,
                        bytes    = 0L,
                        icon     = Icons.Default.Delete,
                        onCancel = vm::cancelDelete,
                    )
                }

                is SftpViewModel.State.Downloading -> {
                    TransferProgress(
                        label = buildString {
                            append(stringResource(R.string.sftp_downloading_label))
                            if (s.totalFiles > 1) append(" (${s.fileIndex}/${s.totalFiles})")
                        },
                        sublabel     = s.filename,
                        location     = s.location,
                        bytes        = s.bytesReceived,
                        icon         = Icons.Default.Download,
                        startedAt    = s.startedAt,
                        onCancel     = vm::cancelDownload,
                        onBackground = vm::sendToBackground,
                    )
                }

                is SftpViewModel.State.Downloaded -> {
                    DownloadComplete(
                        filename     = s.filename,
                        location     = s.location,
                        totalFiles   = s.totalFiles,
                        skippedFiles = s.skippedFiles,
                        startedAt    = s.startedAt,
                        completedAt  = s.completedAt,
                        onOpen       = { vm.openDownloadedFile(s.filename, s.location) },
                        onDone       = vm::dismissDownloaded,
                    )
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
                    ProblemContent(
                        title        = s.message,
                        summary      = null,
                        detail       = s.detail,
                        primaryLabel = stringResource(R.string.action_go_back),
                        onPrimary    = onBack,
                        modifier     = Modifier.align(Alignment.Center).padding(24.dp),
                        icon         = Icons.Default.ErrorOutline,
                        iconTint     = MaterialTheme.colorScheme.error,
                    )
                }

                SftpViewModel.State.Disconnected -> {
                    Text(stringResource(R.string.sftp_disconnected), Modifier.align(Alignment.Center))
                }
            }

            // Background downloads panel — shown whenever there are active or recent transfers.
            // Right padding avoids the FAB column when it is visible.
            if (backgroundTransfers.isNotEmpty()) {
                BackgroundTransfersPanel(
                    transfers  = backgroundTransfers,
                    onCancel   = vm::cancelBackgroundTransfer,
                    onDismiss  = vm::dismissBackgroundTransfer,
                    onOpen     = { vm.openDownloadedFile(it.filename, it.localDir) },
                    modifier   = Modifier.align(Alignment.BottomCenter),
                    endPadding = if (isListing && !selectionMode) 80.dp else 12.dp,
                )
            }
        }
    }

    // Bulk delete confirmation dialog
    pendingBulkDelete?.let { entries ->
        val folderCount = entries.count { it.isDir }
        val fileCount   = entries.count { !it.isDir }
        val message = buildString {
            append(stringResource(R.string.sftp_bulk_delete_message_prefix))
            append(" ")
            if (folderCount > 0) {
                append(stringResource(R.string.sftp_bulk_delete_folders, folderCount))
                if (fileCount > 0) append(" ")
            }
            if (fileCount > 0) {
                append(stringResource(R.string.sftp_bulk_delete_files, fileCount))
            }
            append("\n")
            append(stringResource(R.string.sftp_delete_message_suffix))
        }
        AlertDialog(
            onDismissRequest = { pendingBulkDelete = null },
            title = { Text(stringResource(R.string.sftp_bulk_delete_title)) },
            text  = { Text(message) },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        vm.deleteEntries(entries, currentPath)
                        pendingBulkDelete = null
                        selectionMode = false
                        selectedEntries = emptySet()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error)),
                ) { Text(stringResource(R.string.action_delete_all)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingBulkDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
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
                OutlinedButton(
                    onClick = { vm.deleteEntry(entry, currentPath); entryToDelete = null },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error)),
                ) { Text(stringResource(R.string.action_delete)) }
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
                OutlinedButton(
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

    // Download conflict dialog (single-file downloads only)
    pendingConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = { pendingConflict = null },
            title = { Text(stringResource(R.string.sftp_conflict_title)) },
            text  = { Text(stringResource(R.string.sftp_conflict_message, conflict.entry.name)) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pendingConflict = null; vm.downloadKeepBoth(conflict) }) {
                            Text(stringResource(R.string.action_keep_both))
                        }
                        OutlinedButton(
                            onClick = { pendingConflict = null; vm.downloadOverwrite(conflict) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error)
                            ),
                        ) {
                            Text(stringResource(R.string.action_overwrite))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { pendingConflict = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            },
        )
    }

    // Batch download conflict dialog
    pendingBatchConflict?.let { batchConflict ->
        AlertDialog(
            onDismissRequest = {
                pendingBatchConflict = null
                vm.resolveBatchConflict(SftpViewModel.BatchConflictDecision.CANCEL)
            },
            title = { Text(stringResource(R.string.sftp_batch_conflict_title)) },
            text  = {
                Text(stringResource(R.string.sftp_batch_conflict_message,
                    batchConflict.conflictCount, batchConflict.totalCount))
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row {
                        TextButton(onClick = {
                            pendingBatchConflict = null
                            vm.resolveBatchConflict(SftpViewModel.BatchConflictDecision.SKIP_EXISTING)
                        }) { Text(stringResource(R.string.action_skip_existing)) }
                        TextButton(onClick = {
                            pendingBatchConflict = null
                            vm.resolveBatchConflict(SftpViewModel.BatchConflictDecision.OVERWRITE_ALL)
                        }) {
                            Text(stringResource(R.string.action_overwrite_all),
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = {
                        pendingBatchConflict = null
                        vm.resolveBatchConflict(SftpViewModel.BatchConflictDecision.CANCEL)
                    }) { Text(stringResource(R.string.action_cancel)) }
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
                OutlinedButton(
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
    sublabel: String = "",
    location: String = "",
    bytes: Long,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    startedAt: Long = 0L,
    onCancel: (() -> Unit)? = null,
    onBackground: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .align(Alignment.Center)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (sublabel.isNotEmpty()) {
            Text(
                sublabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (location.isNotEmpty()) {
            Text(
                location,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Always reserve space for size text to prevent layout shifts when bytes become available
        Text(
            if (bytes > 0) formatSize(bytes) else "",
            style = MaterialTheme.typography.bodySmall,
        )
        if (startedAt > 0L) {
            TransferTimestamps(startedAt = startedAt, completedAt = null)
        }
        if (onBackground != null || onCancel != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onBackground != null) {
                    TextButton(onClick = onBackground) {
                        Text(stringResource(R.string.sftp_send_to_background))
                    }
                }
                if (onCancel != null) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SftpEntryItem(
    entry: SftpEntry,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDownloadFolder: () -> Unit,
    onDownloadInBackground: (() -> Unit)?,
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (selectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null, // row onClick handles toggle
                        )
                    }
                    Box {
                        Icon(
                            if (entry.isDir) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                            contentDescription = null,
                            tint = if (entry.isDir) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (entry.isLink) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .align(Alignment.BottomEnd)
                                    .background(
                                        color  = MaterialTheme.colorScheme.tertiary,
                                        shape  = RoundedCornerShape(2.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.SubdirectoryArrowLeft,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.onTertiary,
                                )
                            }
                        }
                    }
                }
            },
            headlineContent = {
                Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                if (!entry.isDir) {
                    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
                    val date = SimpleDateFormat("dd MMM yyyy HH:mm", locale)
                        .format(Date(entry.modTimeSeconds.toLong() * 1000))
                    Text("${formatSize(entry.size)}  ·  $date",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            trailingContent = {
                if (entry.isDir && !entry.isLink && !selectionMode) {
                    IconButton(
                        onClick = onDownloadFolder,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = stringResource(R.string.sftp_download_folder_cd),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else if (!entry.isDir) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = stringResource(R.string.sftp_download_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            if (onDownloadInBackground != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sftp_menu_download_in_background)) },
                    leadingIcon = { Icon(Icons.Default.DownloadForOffline, null) },
                    onClick = { menuExpanded = false; onDownloadInBackground() },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sftp_menu_rename)) },
                leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                onClick = { menuExpanded = false; onRename() },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.sftp_menu_delete),
                        color = MaterialTheme.colorScheme.error,
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

@Composable
private fun BackgroundTransfersPanel(
    transfers: List<BackgroundTransfer>,
    onCancel: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onOpen: (BackgroundTransfer) -> Unit,
    modifier: Modifier = Modifier,
    endPadding: Dp = 12.dp,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = endPadding, top = 8.dp, bottom = 8.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(
                text = stringResource(R.string.sftp_background_downloads_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            transfers.forEach { t ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = when (t.status) {
                            BackgroundTransfer.Status.Done      -> Icons.Default.CheckCircle
                            BackgroundTransfer.Status.Error     -> Icons.Default.ErrorOutline
                            BackgroundTransfer.Status.Cancelled -> Icons.Default.Cancel
                            BackgroundTransfer.Status.Running   -> Icons.Default.Downloading
                        },
                        contentDescription = null,
                        tint = when (t.status) {
                            BackgroundTransfer.Status.Done      -> MaterialTheme.colorScheme.primary
                            BackgroundTransfer.Status.Error     -> MaterialTheme.colorScheme.error
                            BackgroundTransfer.Status.Cancelled -> MaterialTheme.colorScheme.onSurfaceVariant
                            BackgroundTransfer.Status.Running   -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        // Filename and byte count share the top line; the size is pinned
                        // to the right so it never overlaps the timestamp lines below.
                        // A very long filename is ellipsised to make room for the size.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // A completed single-file download's name is tappable (primary
                            // colour + an "open" icon) and opens the file; anything else is plain.
                            val openable = t.status == BackgroundTransfer.Status.Done && t.totalFiles == 1
                            if (openable) {
                                Row(
                                    modifier = Modifier.weight(1f).clickable { onOpen(t) },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = t.filename,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    Icon(
                                        Icons.Default.FileOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            } else {
                                Text(
                                    text = t.filename,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (t.bytesReceived > 0) {
                                Text(
                                    text = formatSize(t.bytesReceived),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        TransferTimestamps(
                            startedAt   = t.startedAt,
                            completedAt = t.completedAt,
                            cancelled   = t.status == BackgroundTransfer.Status.Cancelled,
                        )
                    }
                    IconButton(
                        onClick = {
                            if (t.status == BackgroundTransfer.Status.Running) onCancel(t.id)
                            else onDismiss(t.id)
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(
                                if (t.status == BackgroundTransfer.Status.Running)
                                    R.string.action_cancel
                                else
                                    R.string.sftp_background_dismiss_cd,
                            ),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1_024               -> "$bytes B"
    bytes < 1_048_576           -> "%.1f KB".format(bytes / 1_024.0)
    bytes < 1_073_741_824       -> "%.1f MB".format(bytes / 1_048_576.0)
    else                        -> "%.2f GB".format(bytes / 1_073_741_824.0)
}

// "2026/06/20 15:36:01 CEST" — fixed numeric format, locale-independent digits.
private val transferDateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US)

// Android's ICU often returns only "GMT+2" for non-US zones; this curated map
// restores the usual abbreviation (e.g. CEST) for the regions SSHBorg ships in.
// Pair = (standard-time abbreviation, daylight-time abbreviation).
private val ZONE_ABBREV: Map<String, Pair<String, String>> = mapOf(
    "Europe/Rome"       to ("CET" to "CEST"),
    "Europe/Berlin"     to ("CET" to "CEST"),
    "Europe/Paris"      to ("CET" to "CEST"),
    "Europe/Madrid"     to ("CET" to "CEST"),
    "Europe/Amsterdam"  to ("CET" to "CEST"),
    "Europe/Brussels"   to ("CET" to "CEST"),
    "Europe/Vienna"     to ("CET" to "CEST"),
    "Europe/Zurich"     to ("CET" to "CEST"),
    "Europe/Warsaw"     to ("CET" to "CEST"),
    "Europe/Prague"     to ("CET" to "CEST"),
    "Europe/Stockholm"  to ("CET" to "CEST"),
    "Europe/Copenhagen" to ("CET" to "CEST"),
    "Europe/Oslo"       to ("CET" to "CEST"),
    "Europe/Budapest"   to ("CET" to "CEST"),
    "Europe/Lisbon"     to ("WET" to "WEST"),
    "Europe/London"     to ("GMT" to "BST"),
    "Europe/Dublin"     to ("GMT" to "IST"),
    "Europe/Kyiv"       to ("EET" to "EEST"),
    "Europe/Kiev"       to ("EET" to "EEST"),
    "Europe/Athens"     to ("EET" to "EEST"),
    "Europe/Helsinki"   to ("EET" to "EEST"),
    "Europe/Bucharest"  to ("EET" to "EEST"),
)

private fun zoneAbbreviation(zone: TimeZone, instant: Long): String {
    val daylight = zone.inDaylightTime(Date(instant))
    val icu = zone.getDisplayName(daylight, TimeZone.SHORT, Locale.US)
    // ICU gave a real abbreviation (e.g. "CEST") — use it.
    if (!icu.startsWith("GMT") && !icu.startsWith("UTC")) return icu
    // ICU fell back to a GMT/UTC offset; restore the abbreviation when we know it.
    val mapped = ZONE_ABBREV[zone.id] ?: return icu
    return if (daylight) mapped.second else mapped.first
}

private fun formatTransferTime(epochMillis: Long): String {
    val zone = TimeZone.getDefault()
    return "${transferDateFormat.format(Date(epochMillis))} ${zoneAbbreviation(zone, epochMillis)}"
}

/** Small, dimmed start/finish timestamps shown under a transfer. */
@Composable
private fun TransferTimestamps(startedAt: Long, completedAt: Long?, cancelled: Boolean = false) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    if (startedAt > 0L) {
        Text(
            text = stringResource(R.string.sftp_started_at, formatTransferTime(startedAt)),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (completedAt != null && completedAt > 0L) {
        Text(
            text = stringResource(
                if (cancelled) R.string.sftp_cancelled_at else R.string.sftp_finished_at,
                formatTransferTime(completedAt),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Persistent foreground download-complete screen with start/finish times and a Done button. */
@Composable
private fun BoxScope.DownloadComplete(
    filename: String,
    location: String,
    totalFiles: Int,
    skippedFiles: Int,
    startedAt: Long,
    completedAt: Long,
    onOpen: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        Modifier
            .align(Alignment.Center)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Text(
            stringResource(R.string.sftp_download_complete),
            style = MaterialTheme.typography.titleMedium,
        )
        val singleFile = totalFiles == 1 && skippedFiles == 0
        val summary = when {
            singleFile -> filename
            skippedFiles > 0 ->
                stringResource(R.string.sftp_downloaded_n_files_skipped, totalFiles - skippedFiles, skippedFiles)
            else ->
                stringResource(R.string.sftp_downloaded_n_files, totalFiles)
        }
        // A single file's name is tappable (primary colour + an "open" icon) and opens the
        // file; a multi-file summary is just a count, so it stays plain text.
        if (singleFile) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    Icons.Default.FileOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp).size(16.dp),
                )
            }
        } else {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (location.isNotEmpty()) {
            Text(
                text = location,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TransferTimestamps(startedAt = startedAt, completedAt = completedAt)
        }
        Button(onClick = onDone) {
            Text(stringResource(R.string.action_done))
        }
    }
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
            OutlinedButton(onClick = { onSubmit(pwd) }) { Text(stringResource(R.string.action_connect)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
