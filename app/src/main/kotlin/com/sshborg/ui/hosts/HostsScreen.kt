package com.sshborg.ui.hosts

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sshborg.R
import com.sshborg.data.db.HostEntity
import com.sshborg.service.SessionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostsScreen(
    sessions: List<SessionManager.ActiveSession>,
    onNewTerminal: (hostId: Long, hostLabel: String) -> Unit,
    onResumeTerminal: (sessionId: String) -> Unit,
    onNewSftp: (hostId: Long, hostLabel: String) -> Unit,
    onResumeSftp: (sessionId: String) -> Unit,
    onAddHost: () -> Unit,
    onEditHost: (Long) -> Unit,
    onKeysClick: () -> Unit,
    onSettingsClick: () -> Unit,
    vm: HostsViewModel = viewModel(),
) {
    val hosts       by vm.hosts.collectAsState()
    val confirmExit by vm.confirmExit.collectAsState()
    val context     = LocalContext.current

    // Double-back-to-exit
    var lastBackPress by remember { mutableLongStateOf(0L) }
    val pressBackToExit = stringResource(R.string.hosts_press_back_to_exit)
    BackHandler(enabled = confirmExit) {
        val now = System.currentTimeMillis()
        if (now - lastBackPress < 2_000L) {
            (context as Activity).finish()
        } else {
            lastBackPress = now
            Toast.makeText(context, pressBackToExit, Toast.LENGTH_SHORT).show()
        }
    }

    var hostToDelete by remember { mutableStateOf<HostEntity?>(null) }

    // Bottom sheet state for session picker
    var sessionPickerHost by remember { mutableStateOf<HostEntity?>(null) }
    var sessionPickerType by remember { mutableStateOf(SessionManager.SessionType.Shell) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hosts_title)) },
                actions = {
                    IconButton(onClick = onKeysClick) {
                        Icon(Icons.Default.Key, contentDescription = stringResource(R.string.hosts_manage_keys_cd))
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.hosts_settings_cd))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddHost) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.hosts_add_host_cd))
            }
        },
    ) { padding ->
        if (hosts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.hosts_empty), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(hosts, key = { it.id }) { host ->
                    val shellSessions = sessions.filter {
                        it.hostId == host.id && it.type == SessionManager.SessionType.Shell
                    }
                    val sftpSessions = sessions.filter {
                        it.hostId == host.id && it.type == SessionManager.SessionType.Sftp
                    }
                    HostItem(
                        host          = host,
                        shellCount    = shellSessions.size,
                        sftpCount     = sftpSessions.size,
                        onClick       = {
                            when (shellSessions.size) {
                                0    -> onNewTerminal(host.id, host.label)
                                1    -> onResumeTerminal(shellSessions[0].id)
                                else -> { sessionPickerHost = host; sessionPickerType = SessionManager.SessionType.Shell }
                            }
                        },
                        onSftp        = {
                            when (sftpSessions.size) {
                                0    -> onNewSftp(host.id, host.label)
                                1    -> onResumeSftp(sftpSessions[0].id)
                                else -> { sessionPickerHost = host; sessionPickerType = SessionManager.SessionType.Sftp }
                            }
                        },
                        onEdit        = { onEditHost(host.id) },
                        onDelete      = { hostToDelete = host },
                        onNewTerminal = { onNewTerminal(host.id, host.label) },
                        onNewSftp     = { onNewSftp(host.id, host.label) },
                    )
                }
            }
        }
    }

    // Delete confirmation
    hostToDelete?.let { host ->
        AlertDialog(
            onDismissRequest = { hostToDelete = null },
            title = { Text(stringResource(R.string.hosts_delete_title)) },
            text  = { Text(stringResource(R.string.hosts_delete_message, host.label)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteHost(host); hostToDelete = null }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { hostToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // Session picker bottom sheet (for hosts with multiple active sessions)
    sessionPickerHost?.let { host ->
        val activeSessions = sessions.filter {
            it.hostId == host.id && it.type == sessionPickerType
        }
        SessionPickerSheet(
            host        = host,
            type        = sessionPickerType,
            sessions    = activeSessions,
            onResume    = { id ->
                sessionPickerHost = null
                if (sessionPickerType == SessionManager.SessionType.Shell) onResumeTerminal(id)
                else onResumeSftp(id)
            },
            onNew       = {
                sessionPickerHost = null
                if (sessionPickerType == SessionManager.SessionType.Shell) onNewTerminal(host.id, host.label)
                else onNewSftp(host.id, host.label)
            },
            onDismiss   = { sessionPickerHost = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SessionPickerSheet(
    host: HostEntity,
    type: SessionManager.SessionType,
    sessions: List<SessionManager.ActiveSession>,
    onResume: (String) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    val typeName = if (type == SessionManager.SessionType.Shell)
        stringResource(R.string.session_type_terminal)
    else
        stringResource(R.string.session_type_files)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.session_picker_title, host.label, typeName),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        sessions.forEachIndexed { index, session ->
            val statusText = when (session.status) {
                SessionManager.Status.Connected    -> stringResource(R.string.session_status_connected)
                SessionManager.Status.Connecting   -> stringResource(R.string.session_status_connecting)
                SessionManager.Status.Disconnected -> stringResource(R.string.session_status_disconnected)
                SessionManager.Status.Error        -> stringResource(R.string.session_status_error)
            }
            ListItem(
                modifier = Modifier.combinedClickable(onClick = { onResume(session.id) }),
                leadingContent = {
                    Icon(
                        if (type == SessionManager.SessionType.Shell) Icons.Default.Terminal else Icons.Default.Folder,
                        contentDescription = null,
                    )
                },
                headlineContent  = { Text(stringResource(R.string.session_picker_session_label, index + 1)) },
                supportingContent = { Text(statusText) },
            )
            HorizontalDivider(thickness = 0.5.dp)
        }
        ListItem(
            modifier = Modifier.combinedClickable(onClick = onNew),
            leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.session_picker_new_session, typeName)) },
        )
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HostItem(
    host: HostEntity,
    shellCount: Int,
    sftpCount: Int,
    onClick: () -> Unit,
    onSftp: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onNewTerminal: () -> Unit,
    onNewSftp: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ListItem(
        modifier = Modifier.combinedClickable(
            onClick     = onClick,
            onLongClick = { menuExpanded = true },
        ),
        headlineContent = { Text(host.label) },
        supportingContent = {
            Text(
                "${host.username}@${host.hostname}:${host.port}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        leadingContent = {
            // Show active session badge if any sessions are running
            val totalActive = shellCount + sftpCount
            BadgedBox(
                badge = {
                    if (totalActive > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text("$totalActive")
                        }
                    }
                }
            ) {
                Icon(Icons.Default.Computer, contentDescription = null)
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.hosts_options_cd))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    // "Connect" — resumes if 1 active, picks if >1, creates if 0
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (shellCount > 0)
                                    stringResource(R.string.host_menu_resume_terminal, shellCount)
                                else
                                    stringResource(R.string.host_menu_connect)
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Terminal, null) },
                        onClick = { menuExpanded = false; onClick() },
                    )
                    if (shellCount > 0) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.host_menu_new_terminal)) },
                            leadingIcon = { Icon(Icons.Default.Add, null) },
                            onClick = { menuExpanded = false; onNewTerminal() },
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (sftpCount > 0)
                                    stringResource(R.string.host_menu_resume_files, sftpCount)
                                else
                                    stringResource(R.string.host_menu_files)
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Folder, null) },
                        onClick = { menuExpanded = false; onSftp() },
                    )
                    if (sftpCount > 0) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.host_menu_new_files_session)) },
                            leadingIcon = { Icon(Icons.Default.Add, null) },
                            onClick = { menuExpanded = false; onNewSftp() },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_edit)) },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menuExpanded = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        },
    )
    HorizontalDivider(thickness = 0.5.dp)
}
