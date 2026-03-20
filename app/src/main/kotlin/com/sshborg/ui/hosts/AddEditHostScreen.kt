package com.sshborg.ui.hosts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sshborg.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditHostScreen(
    hostId: Long,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    vm: AddEditHostViewModel = viewModel(),
) {
    val isNew = hostId == Screen.AddEditHost.NEW_ID

    LaunchedEffect(hostId) { vm.loadHost(hostId) }

    val label by vm.label.collectAsState()
    val hostname by vm.hostname.collectAsState()
    val port by vm.port.collectAsState()
    val username by vm.username.collectAsState()
    val useKey by vm.useKey.collectAsState()
    val selectedKeyId by vm.selectedKeyId.collectAsState()
    val agentForwarding by vm.agentForwarding.collectAsState()
    val jumpHosts by vm.jumpHosts.collectAsState()
    val keys by vm.keys.collectAsState()

    var passwordVisible by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var keyMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Add Host" else "Edit Host") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = label, onValueChange = { vm.label.value = it },
                label = { Text("Label (optional)") }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = hostname, onValueChange = { vm.hostname.value = it },
                label = { Text("Hostname / IP") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = username, onValueChange = { vm.username.value = it },
                    label = { Text("Username") }, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = port, onValueChange = { vm.port.value = it },
                    label = { Text("Port") }, modifier = Modifier.width(90.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }

            HorizontalDivider()
            Text("Authentication", style = MaterialTheme.typography.titleSmall)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = useKey, onCheckedChange = { vm.useKey.value = it })
                Spacer(Modifier.width(8.dp))
                Text(if (useKey) "SSH Key" else "Password")
            }

            if (!useKey) {
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password") }, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(if (passwordVisible) "Hide" else "Show")
                        }
                    },
                )
            } else {
                // Key selector
                Box {
                    OutlinedTextField(
                        value = keys.find { it.id == selectedKeyId }?.label ?: "Select a key…",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("SSH Key") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            TextButton(onClick = { keyMenuExpanded = true }) { Text("Change") }
                        },
                    )
                    DropdownMenu(expanded = keyMenuExpanded, onDismissRequest = { keyMenuExpanded = false }) {
                        keys.forEach { key ->
                            DropdownMenuItem(
                                text = { Text(key.label) },
                                onClick = { vm.selectedKeyId.value = key.id; keyMenuExpanded = false },
                            )
                        }
                        if (keys.isEmpty()) {
                            DropdownMenuItem(text = { Text("No keys – go add one first") }, onClick = { keyMenuExpanded = false })
                        }
                    }
                }
            }

            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = agentForwarding, onCheckedChange = { vm.agentForwarding.value = it })
                Spacer(Modifier.width(8.dp))
                Text("Agent Forwarding")
            }

            if (agentForwarding) {
                OutlinedTextField(
                    value = jumpHosts,
                    onValueChange = { vm.jumpHosts.value = it },
                    label = { Text("Jump hosts (optional)") },
                    placeholder = { Text("host1:22,host2:2222") },
                    supportingText = { Text("Comma-separated list of SSH jump hosts in host:port format") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { vm.save(onSaved) },
                modifier = Modifier.fillMaxWidth(),
                enabled = hostname.isNotBlank() && username.isNotBlank(),
            ) {
                Text("Save")
            }
        }
    }
}
