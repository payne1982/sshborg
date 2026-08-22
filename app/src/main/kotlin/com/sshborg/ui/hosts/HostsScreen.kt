package com.sshborg.ui.hosts

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sshborg.R
import com.sshborg.data.db.GroupEntity
import com.sshborg.data.db.HostEntity
import com.sshborg.isTouchless
import com.sshborg.ui.common.onMenuKey
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
    val groups      by vm.groups.collectAsState()
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

    var hostToDelete  by remember { mutableStateOf<HostEntity?>(null) }
    var groupToEdit   by remember { mutableStateOf<GroupEntity?>(null) }
    var groupToDelete by remember { mutableStateOf<GroupEntity?>(null) }

    // Bottom sheet state for session picker
    var sessionPickerHost by remember { mutableStateOf<HostEntity?>(null) }
    var sessionPickerType by remember { mutableStateOf(SessionManager.SessionType.Shell) }

    // Touchless devices can't long-press a group header: show a focusable ⋮ instead.
    val isTouchless = remember { isTouchless(context) }

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
                    IconButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sshborg.com")))
                    }) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = stringResource(R.string.hosts_help_cd))
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
        // Host rows for one section; groupColor tints the leading icon (null = default).
        fun LazyListScope.hostItems(list: List<HostEntity>, groupColor: Color?) {
            items(list, key = { it.id }) { host ->
                val shellSessions = sessions.filter {
                    it.hostId == host.id && it.type == SessionManager.SessionType.Shell
                }
                val sftpSessions = sessions.filter {
                    it.hostId == host.id && it.type == SessionManager.SessionType.Sftp
                }
                HostItem(
                    host          = host,
                    groupColor    = groupColor,
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
                    onClone       = { vm.cloneHost(host) { newId -> onEditHost(newId) } },
                    onDelete      = { hostToDelete = host },
                    onNewTerminal = { onNewTerminal(host.id, host.label) },
                    onNewSftp     = { onNewSftp(host.id, host.label) },
                )
            }
        }

        if (hosts.isEmpty() && groups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.hosts_empty), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            // Ungrouped hosts first (no header — with zero groups the list looks
            // exactly as it always did), then one collapsible section per group.
            // Hosts pointing at a missing group (e.g. odd imports) fall back to ungrouped.
            val groupIds = groups.map { it.id }.toSet()
            val ungrouped = hosts.filter { it.groupId == null || it.groupId !in groupIds }
            val hostsByGroup = hosts.filter { it.groupId != null && it.groupId in groupIds }
                .groupBy { it.groupId!! }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                // Extra bottom space so the last host row can scroll clear of the
                // floating "+" button (FAB ~56dp + 16dp margins), which otherwise
                // covers it when the list fills the screen.
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                hostItems(ungrouped, groupColor = null)
                groups.forEach { group ->
                    item(key = "g-${group.id}") {
                        GroupHeader(
                            group    = group,
                            count    = hostsByGroup[group.id].orEmpty().size,
                            onToggle = { vm.toggleGroupCollapsed(group) },
                            onEdit   = { groupToEdit = group },
                            onDelete = { groupToDelete = group },
                            showOverflow = isTouchless,
                        )
                    }
                    if (!group.collapsed) {
                        hostItems(hostsByGroup[group.id].orEmpty(), groupColor = Color(group.color))
                    }
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
                OutlinedButton(
                    onClick = { vm.deleteHost(host); hostToDelete = null },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error)),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { hostToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // Group edit (rename / recolor)
    groupToEdit?.let { group ->
        GroupDialog(
            title        = stringResource(R.string.group_dialog_title_edit),
            initialName  = group.name,
            initialColor = group.color,
            onConfirm    = { name, color ->
                vm.saveGroup(group.copy(name = name, color = color))
                groupToEdit = null
            },
            onDismiss    = { groupToEdit = null },
        )
    }

    // Group delete confirmation (hosts are kept, they just become ungrouped)
    groupToDelete?.let { group ->
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            title = { Text(stringResource(R.string.group_delete_title)) },
            text  = { Text(stringResource(R.string.group_delete_message, group.name)) },
            confirmButton = {
                OutlinedButton(
                    onClick = { vm.deleteGroup(group); groupToDelete = null },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error)),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { groupToDelete = null }) {
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
private fun GroupHeader(
    group: GroupEntity,
    count: Int,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    showOverflow: Boolean = false,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        ListItem(
            modifier = Modifier
                .onMenuKey { menuExpanded = true }   // D-pad "options" key opens the group menu
                .combinedClickable(
                    onClick     = onToggle,
                    onLongClick = { menuExpanded = true },
                ),
            leadingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        if (group.collapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                    Box(Modifier.size(12.dp).background(Color(group.color), CircleShape))
                }
            },
            headlineContent = {
                Text(
                    "${group.name} ($count)",
                    style = MaterialTheme.typography.titleSmall,
                )
            },
            trailingContent = if (showOverflow) {
                {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.hosts_options_cd))
                    }
                }
            } else null,
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
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
    HorizontalDivider(thickness = 0.5.dp)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HostItem(
    host: HostEntity,
    groupColor: Color?,
    shellCount: Int,
    sftpCount: Int,
    onClick: () -> Unit,
    onSftp: () -> Unit,
    onEdit: () -> Unit,
    onClone: () -> Unit,
    onDelete: () -> Unit,
    onNewTerminal: () -> Unit,
    onNewSftp: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ListItem(
        modifier = Modifier
            .onMenuKey { menuExpanded = true }   // D-pad "options" key opens the host menu
            .combinedClickable(
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Default.Computer,
                    contentDescription = null,
                    // Host's own color wins over the group color
                    tint = host.color?.let { Color(it) } ?: groupColor ?: LocalContentColor.current,
                )
                if (shellCount > 0 || sftpCount > 0) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        if (shellCount > 0) {
                            Box(
                                modifier = Modifier
                                    .clickable { onClick() }
                                    .padding(4.dp),
                            ) {
                                SessionBadge(
                                    count = shellCount,
                                    color = MaterialTheme.colorScheme.primary,
                                    onColor = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                        if (sftpCount > 0) {
                            Box(
                                modifier = Modifier
                                    .clickable { onSftp() }
                                    .padding(4.dp),
                            ) {
                                SftpBadge(count = sftpCount)
                            }
                        }
                    }
                }
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
                        text = { Text(stringResource(R.string.action_duplicate)) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                        onClick = { menuExpanded = false; onClone() },
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


@Composable
private fun SessionBadge(count: Int, color: Color, onColor: Color) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$count",
            color = onColor,
            fontSize = 9.sp,
            lineHeight = 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SftpBadge(count: Int) {
    val textColor = if (isSystemInDarkTheme()) Color.Black else Color.White
    Box(contentAlignment = Alignment.Center) {
        Image(
            painter = rememberVectorPainter(Icons.Default.Folder),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            colorFilter = ColorFilter.tint(Color(0xFFF9A825)),
            modifier = Modifier.size(width = 18.dp, height = 22.dp),
        )
        Text(
            text = "$count",
            color = textColor,
            fontSize = 9.sp,
            lineHeight = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
