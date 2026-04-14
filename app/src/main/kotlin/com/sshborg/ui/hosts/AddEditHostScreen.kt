package com.sshborg.ui.hosts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sshborg.R
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
    val portForwardings by vm.portForwardings.collectAsState()
    val jumpMode by vm.jumpMode.collectAsState()
    val jumpHostIds by vm.jumpHostIds.collectAsState()
    val availableJumpHosts by vm.availableJumpHosts.collectAsState()
    val keys by vm.keys.collectAsState()
    val password by vm.password.collectAsState()

    var passwordVisible by remember { mutableStateOf(false) }
    var keyMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isNew) stringResource(R.string.add_host_title)
                        else stringResource(R.string.edit_host_title)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = label, onValueChange = { vm.label.value = it },
                label = { Text(stringResource(R.string.host_field_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = hostname,
                onValueChange = { vm.hostname.value = it.filter { c -> c.isLetterOrDigit() || c in ".-:_" } },
                label = { Text(stringResource(R.string.host_field_hostname)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = username, onValueChange = { vm.username.value = it },
                    label = { Text(stringResource(R.string.host_field_username)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { vm.port.value = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.host_field_port)) },
                    modifier = Modifier.width(90.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }

            HorizontalDivider()
            Text(stringResource(R.string.host_section_authentication), style = MaterialTheme.typography.titleSmall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    RadioButton(selected = !useKey, onClick = { vm.useKey.value = false })
                    Text(stringResource(R.string.host_auth_password))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    RadioButton(selected = useKey, onClick = { vm.useKey.value = true })
                    Text(stringResource(R.string.host_auth_ssh_key))
                }
            }

            if (!useKey) {
                OutlinedTextField(
                    value = password, onValueChange = { vm.password.value = it },
                    label = { Text(stringResource(R.string.host_field_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(
                                if (passwordVisible) stringResource(R.string.action_hide)
                                else stringResource(R.string.action_show)
                            )
                        }
                    },
                )
            } else {
                // Key selector
                Box {
                    OutlinedTextField(
                        value = keys.find { it.id == selectedKeyId }?.label
                            ?: stringResource(R.string.host_key_select_placeholder),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.host_auth_ssh_key)) },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            TextButton(onClick = { keyMenuExpanded = true }) {
                                Text(stringResource(R.string.host_key_change))
                            }
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
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.host_key_no_keys)) },
                                onClick = { keyMenuExpanded = false }
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = agentForwarding, onCheckedChange = { vm.agentForwarding.value = it })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.host_agent_forwarding))
            }

            HorizontalDivider()
            Text(stringResource(R.string.host_section_jump_hosts), style = MaterialTheme.typography.titleSmall)

            // Mode selector
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    RadioButton(
                        selected = jumpMode == "simple",
                        onClick  = { vm.jumpMode.value = "simple" },
                    )
                    Text(stringResource(R.string.host_jump_mode_simple))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    RadioButton(
                        selected = jumpMode == "host_list",
                        onClick  = { vm.jumpMode.value = "host_list" },
                    )
                    Text(stringResource(R.string.host_jump_mode_host_list))
                }
            }

            if (jumpMode == "simple") {
                OutlinedTextField(
                    value = jumpHosts,
                    onValueChange = { vm.jumpHosts.value = it },
                    label = { Text(stringResource(R.string.host_field_jump_hosts)) },
                    placeholder = { Text(stringResource(R.string.host_jump_hosts_placeholder)) },
                    supportingText = { Text(stringResource(R.string.host_jump_hosts_supporting)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            } else {
                // Host-list mode: one row per hop
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    jumpHostIds.forEachIndexed { index, selectedId ->
                        JumpHostRow(
                            selectedId       = selectedId,
                            options          = availableJumpHosts,
                            onSelect         = { newId ->
                                vm.jumpHostIds.value = vm.jumpHostIds.value.toMutableList()
                                    .also { it[index] = newId }
                            },
                            onRemove         = {
                                vm.jumpHostIds.value = vm.jumpHostIds.value.toMutableList()
                                    .also { it.removeAt(index) }
                            },
                        )
                    }
                    OutlinedButton(
                        onClick   = { vm.jumpHostIds.value = vm.jumpHostIds.value + 0L },
                        modifier  = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.host_jump_add))
                    }
                }
            }

            HorizontalDivider()
            Text(stringResource(R.string.host_section_port_forwarding), style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value = portForwardings,
                onValueChange = { vm.portForwardings.value = it },
                label = { Text(stringResource(R.string.host_field_port_forwarding)) },
                placeholder = { Text(stringResource(R.string.host_port_forwarding_placeholder)) },
                supportingText = { Text(stringResource(R.string.host_port_forwarding_supporting)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { vm.save(onSaved) },
                modifier = Modifier.fillMaxWidth(),
                enabled = hostname.isNotBlank() && username.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JumpHostRow(
    selectedId: Long,
    options: List<AddEditHostViewModel.JumpHostOption>,
    onSelect: (Long) -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.find { it.host.id == selectedId }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = selected?.host?.label
                    ?: stringResource(R.string.host_jump_select_placeholder),
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                singleLine = true,
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    option.host.label,
                                    color = if (option.isSelectable) LocalContentColor.current
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                )
                                if (!option.isSelectable) {
                                    Text(
                                        stringResource(R.string.host_jump_no_password),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    )
                                }
                            }
                        },
                        onClick = {
                            if (option.isSelectable) {
                                onSelect(option.host.id)
                                expanded = false
                            }
                        },
                        enabled = option.isSelectable,
                    )
                }
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Remove, contentDescription = null,
                tint = MaterialTheme.colorScheme.error)
        }
    }
}
