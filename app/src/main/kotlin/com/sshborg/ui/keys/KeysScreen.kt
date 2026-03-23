package com.sshborg.ui.keys

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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sshborg.data.db.SshKeyEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeysScreen(onBack: () -> Unit, vm: KeysViewModel = viewModel()) {
    val keys by vm.keys.collectAsState()
    var showGenerateDialog by remember { mutableStateOf(false) }
    var keyToDelete by remember { mutableStateOf<SshKeyEntity?>(null) }
    var expandedKeyId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SSH Keys") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showGenerateDialog = true }) {
                Icon(Icons.Default.Add, "Generate key")
            }
        },
    ) { padding ->
        if (keys.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No keys yet.\nTap + to generate one.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(keys, key = { it.id }) { key ->
                    KeyItem(
                        key = key,
                        expanded = expandedKeyId == key.id,
                        onExpand = { expandedKeyId = if (expandedKeyId == key.id) null else key.id },
                        onDelete = { keyToDelete = key },
                    )
                }
            }
        }
    }

    if (showGenerateDialog) {
        GenerateKeyDialog(
            onGenerate = { label, type, size -> vm.generateKey(label, type, size) {}; showGenerateDialog = false },
            onDismiss = { showGenerateDialog = false },
        )
    }

    keyToDelete?.let { key ->
        AlertDialog(
            onDismissRequest = { keyToDelete = null },
            title = { Text("Delete key") },
            text = { Text("Delete \"${key.label}\"? Any hosts using this key will fall back to password auth.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteKey(key); keyToDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { keyToDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun KeyItem(
    key: SshKeyEntity,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDelete: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Column {
        ListItem(
            headlineContent = { Text(key.label) },
            supportingContent = { Text(key.keyType, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) },
            leadingContent = { Icon(Icons.Default.Key, null) },
            trailingContent = {
                Row {
                    IconButton(onClick = onExpand) {
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "Show public key")
                    }
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
                }
            },
        )
        if (expanded) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Public key (add to ~/.ssh/authorized_keys):", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(key.publicKey, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            clipboard.setText(AnnotatedString(key.publicKey))
                            copied = true
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (copied) "Copied!" else "Copy")
                    }
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp)
    }
}

@Composable
private fun GenerateKeyDialog(onGenerate: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("ed25519") }
    var rsaBits by remember { mutableStateOf("4096") }
    var ecdsaCurve by remember { mutableStateOf("256") }
    val types = listOf("ed25519", "rsa", "ecdsa")
    val ecdsaCurves = listOf("256", "384", "521")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate SSH Key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text("Label") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Key type", style = MaterialTheme.typography.labelMedium)
                types.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = type == t, onClick = { type = t })
                        Spacer(Modifier.width(4.dp))
                        Text(t.uppercase())
                    }
                }

                when (type) {
                    "ed25519" -> Text(
                        "Fixed size: 256 bit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    "rsa" -> OutlinedTextField(
                        value = rsaBits,
                        onValueChange = { if (it.all(Char::isDigit)) rsaBits = it },
                        label = { Text("Size (bit)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    "ecdsa" -> {
                        Text("Curve (bit)", style = MaterialTheme.typography.labelMedium)
                        ecdsaCurves.forEach { curve ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = ecdsaCurve == curve, onClick = { ecdsaCurve = curve })
                                Spacer(Modifier.width(4.dp))
                                Text("P-$curve")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val size = when (type) {
                "rsa"   -> rsaBits.ifBlank { "4096" }
                "ecdsa" -> ecdsaCurve
                else    -> ""
            }
            TextButton(
                onClick = { onGenerate(label.ifBlank { type.uppercase() + " Key" }, type, size) },
                enabled = type != "rsa" || rsaBits.toIntOrNull()?.let { it >= 1024 } == true,
            ) { Text("Generate") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
