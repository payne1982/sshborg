package com.sshborg.ui.hosts

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sshborg.data.db.HostEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostsScreen(
    onHostClick: (Long) -> Unit,
    onAddHost: () -> Unit,
    onEditHost: (Long) -> Unit,
    onKeysClick: () -> Unit,
    vm: HostsViewModel = viewModel(),
) {
    val hosts by vm.hosts.collectAsState()
    var hostToDelete by remember { mutableStateOf<HostEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SSHBorg") },
                actions = {
                    IconButton(onClick = onKeysClick) {
                        Icon(Icons.Default.Key, contentDescription = "Manage keys")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddHost) {
                Icon(Icons.Default.Add, contentDescription = "Add host")
            }
        },
    ) { padding ->
        if (hosts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No hosts yet.\nTap + to add one.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(hosts, key = { it.id }) { host ->
                    HostItem(
                        host = host,
                        onClick = { onHostClick(host.id) },
                        onEdit = { onEditHost(host.id) },
                        onDelete = { hostToDelete = host },
                    )
                }
            }
        }
    }

    hostToDelete?.let { host ->
        AlertDialog(
            onDismissRequest = { hostToDelete = null },
            title = { Text("Delete host") },
            text = { Text("Delete \"${host.label}\"?") },
            confirmButton = {
                TextButton(onClick = { vm.deleteHost(host); hostToDelete = null }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { hostToDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HostItem(
    host: HostEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ListItem(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
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
            Icon(Icons.Default.Computer, contentDescription = null)
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Connect") },
                        leadingIcon = { Icon(Icons.Default.Terminal, null) },
                        onClick = { menuExpanded = false; onClick() },
                    )
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menuExpanded = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        },
    )
    HorizontalDivider(thickness = 0.5.dp)
}
