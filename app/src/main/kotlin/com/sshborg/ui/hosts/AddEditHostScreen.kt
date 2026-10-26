package com.sshborg.ui.hosts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sshborg.R
import com.sshborg.Screen
import com.sshborg.data.db.GroupEntity
import com.sshborg.isTouchless
import com.sshborg.ui.common.TvSelectField
import com.sshborg.ui.common.TvTapField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditHostScreen(
    hostId: Long,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    vm: AddEditHostViewModel = viewModel(),
) {
    val isNew = hostId == Screen.AddEditHost.NEW_ID

    // On a touchless device (TV/D-pad) the free-text fields become "tap to edit" rows:
    // navigating onto one no longer forces the on-screen keyboard to grab focus — you
    // move with ↑/↓ and press OK to edit in a dialog. Touch devices keep the inline fields.
    val context = LocalContext.current
    val touchless = remember { isTouchless(context) }

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
    val sftpStartMode by vm.sftpStartMode.collectAsState()
    val sftpStartDir by vm.sftpStartDir.collectAsState()
    val sftpShowHidden by vm.sftpShowHidden.collectAsState()
    val allowLegacyCiphers by vm.allowLegacyCiphers.collectAsState()
    val mosh by vm.mosh.collectAsState()
    val moshFamily by vm.moshFamily.collectAsState()
    val moshPorts by vm.moshPorts.collectAsState()
    val moshCommand by vm.moshCommand.collectAsState()
    val jumpConfigured by vm.jumpConfigured.collectAsState()
    // Not an option on this row — the hop's own switch decides — but the form has to be able to
    // say what will happen, and which hop it is that says so.
    val moshBastion by vm.moshBastion.collectAsState()
    val groups by vm.groups.collectAsState()
    val groupId by vm.groupId.collectAsState()
    val hostColor by vm.hostColor.collectAsState()
    val hasStoredHostKeys by vm.hasStoredHostKeys.collectAsState()
    val resetHostKeys by vm.resetHostKeys.collectAsState()

    var passwordVisible by remember { mutableStateOf(false) }
    var keyMenuExpanded by remember { mutableStateOf(false) }
    var groupMenuExpanded by remember { mutableStateOf(false) }
    var showNewGroupDialog by remember { mutableStateOf(false) }
    var showHostColorDialog by remember { mutableStateOf(false) }

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
            // "Next" on the keyboard advances to the following field; this makes the form
            // fillable with a D-pad/remote on a TV and is a no-op change for touch users.
            val focusManager = LocalFocusManager.current
            val nextField = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })

            HostField(
                value = label, onValueChange = { vm.label.value = it },
                label = stringResource(R.string.host_field_label),
                touchless = touchless,
                modifier = Modifier.fillMaxWidth(),
                imeAction = ImeAction.Next, keyboardActions = nextField,
            )
            HostField(
                value = hostname,
                onValueChange = { vm.hostname.value = it.filter { c -> c.isLetterOrDigit() || c in ".-:_" } },
                label = stringResource(R.string.host_field_hostname),
                touchless = touchless,
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, keyboardActions = nextField,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HostField(
                    value = username, onValueChange = { vm.username.value = it },
                    label = stringResource(R.string.host_field_username),
                    touchless = touchless,
                    modifier = Modifier.weight(1f),
                    imeAction = ImeAction.Next, keyboardActions = nextField,
                )
                HostField(
                    value = port,
                    onValueChange = { vm.port.value = it.filter { c -> c.isDigit() } },
                    label = stringResource(R.string.host_field_port),
                    touchless = touchless,
                    modifier = Modifier.width(90.dp),
                    keyboardType = KeyboardType.Number, imeAction = ImeAction.Next, keyboardActions = nextField,
                )
            }
            val selectedGroup = groups.find { it.id == groupId }
            val groupItems: @Composable (dismiss: () -> Unit) -> Unit = { dismiss ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.host_group_none)) },
                    onClick = { vm.groupId.value = null; dismiss() },
                )
                groups.forEach { group ->
                    DropdownMenuItem(
                        leadingIcon = { Box(Modifier.size(14.dp).background(Color(group.color), CircleShape)) },
                        text = { Text(group.name) },
                        onClick = { vm.groupId.value = group.id; dismiss() },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Default.Add, null) },
                    text = { Text(stringResource(R.string.host_group_new)) },
                    onClick = { dismiss(); showNewGroupDialog = true },
                )
            }
            if (touchless) {
                TvSelectField(
                    label = stringResource(R.string.host_group_label),
                    valueText = selectedGroup?.name ?: stringResource(R.string.host_group_none),
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = selectedGroup?.let { g ->
                        { Box(Modifier.size(14.dp).background(Color(g.color), CircleShape)) }
                    },
                    menuItems = groupItems,
                )
            } else {
                ExposedDropdownMenuBox(
                    expanded = groupMenuExpanded,
                    onExpandedChange = { groupMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedGroup?.name ?: stringResource(R.string.host_group_none),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.host_group_label)) },
                        leadingIcon = selectedGroup?.let { g ->
                            { Box(Modifier.size(14.dp).background(Color(g.color), CircleShape)) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(groupMenuExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        singleLine = true,
                    )
                    ExposedDropdownMenu(
                        expanded = groupMenuExpanded,
                        onDismissRequest = { groupMenuExpanded = false },
                    ) {
                        groupItems { groupMenuExpanded = false }
                    }
                }
            }
            // The colour and the mosh switch share a line. They have nothing to do with each
            // other; the colour row simply left the right half of its line empty, and that is
            // where the switch was asked for.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showHostColorDialog = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val groupColor = groups.find { it.id == groupId }?.color
                    val shownColor = hostColor ?: groupColor
                    Box(
                        Modifier
                            .size(28.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .padding(3.dp)
                            .then(
                                if (shownColor != null)
                                    Modifier.background(Color(shownColor), CircleShape)
                                else Modifier
                            )
                    )
                    Column {
                        Text(stringResource(R.string.host_color_label), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(
                                if (hostColor != null) R.string.host_color_custom
                                else R.string.host_color_auto
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // The whole row stops responding when a jump chain is configured, not only the
                // box: a tap on the label must not be able to turn on what the box refuses, and
                // the line underneath says why it does.
                Row(
                    modifier = Modifier
                        .clickable(enabled = !jumpConfigured) { vm.mosh.value = !mosh }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = mosh && !jumpConfigured,
                        onCheckedChange = { vm.mosh.value = it },
                        enabled = !jumpConfigured,
                    )
                    Column {
                        Text(
                            stringResource(R.string.host_mosh_label),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (jumpConfigured) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            stringResource(R.string.host_mosh_experimental),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // Why the box will not move, and — when the first hop settles it anyway — what is
            // going to happen instead. The second sentence is the one that matters: a chain that
            // quietly became a mosh session would otherwise be visible nowhere at all.
            if (jumpConfigured) {
                val bastion = moshBastion
                Text(
                    if (bastion == null) stringResource(R.string.host_mosh_no_jump)
                    else stringResource(R.string.host_mosh_via_bastion, bastion.label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                if (touchless) {
                    HostField(
                        value = password, onValueChange = { vm.password.value = it },
                        label = stringResource(R.string.host_field_password),
                        touchless = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardType = KeyboardType.Password, isPassword = true,
                    )
                } else {
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
                }
            } else {
                // Key selector
                val keyValueText = keys.find { it.id == selectedKeyId }?.label
                    ?: stringResource(R.string.host_key_select_placeholder)
                val keyItems: @Composable (dismiss: () -> Unit) -> Unit = { dismiss ->
                    keys.forEach { key ->
                        DropdownMenuItem(
                            text = { Text(key.label) },
                            onClick = { vm.selectedKeyId.value = key.id; dismiss() },
                        )
                    }
                    if (keys.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.host_key_no_keys)) },
                            onClick = { dismiss() },
                        )
                    }
                }
                if (touchless) {
                    TvSelectField(
                        label = stringResource(R.string.host_auth_ssh_key),
                        valueText = keyValueText,
                        modifier = Modifier.fillMaxWidth(),
                        menuItems = keyItems,
                    )
                } else {
                    Box {
                        OutlinedTextField(
                            value = keyValueText,
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
                            keyItems { keyMenuExpanded = false }
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = allowLegacyCiphers, onCheckedChange = { vm.allowLegacyCiphers.value = it })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.host_allow_legacy_ciphers))
            }

            // The three things about a mosh session that depend on somebody else's network and
            // somebody else's server, so none of them can be decided here. They belong to the
            // machine that runs mosh-server, which is why a chain walked from a bastion reads
            // the bastion's own three answers and never these.
            if (mosh) {
                HorizontalDivider()
                Text(stringResource(R.string.host_section_mosh), style = MaterialTheme.typography.titleSmall)

                HostChoiceField(
                    label = stringResource(R.string.host_mosh_family_label),
                    selected = moshFamily,
                    options = listOf(
                        "auto" to R.string.host_mosh_family_auto,
                        "ipv4" to R.string.host_mosh_family_ipv4,
                        "ipv6" to R.string.host_mosh_family_ipv6,
                    ),
                    onSelect = { vm.moshFamily.value = it },
                    touchless = touchless,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.host_mosh_family_supporting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HostField(
                    value = moshPorts,
                    onValueChange = { vm.moshPorts.value = it },
                    label = stringResource(R.string.host_mosh_ports_label),
                    touchless = touchless,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Uri,
                    placeholder = stringResource(R.string.host_mosh_ports_placeholder),
                    supporting = stringResource(R.string.host_mosh_ports_supporting),
                )

                HostField(
                    value = moshCommand,
                    onValueChange = { vm.moshCommand.value = it },
                    label = stringResource(R.string.host_mosh_command_label),
                    touchless = touchless,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Uri,
                    placeholder = stringResource(R.string.host_mosh_command_placeholder),
                    supporting = stringResource(R.string.host_mosh_command_supporting),
                )
            }

            HorizontalDivider()
            Text(stringResource(R.string.host_section_jump_hosts), style = MaterialTheme.typography.titleSmall)

            // With mosh on the chain is not tunnelled: it is walked from its first hop, which
            // therefore has to be a host of yours that has mosh of its own. Said here and not
            // only next to the switch, because this is the section where that hop is chosen.
            if (mosh) {
                Text(
                    stringResource(R.string.host_jump_mosh_chain),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Two choices side by side, kept as radios on purpose: both are visible at once,
            // which is what makes it read as "one or the other" rather than as a field whose
            // value happens to be one of them.
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // The host list first, because it is the default and the one that can carry
                // a hop's own credentials; the text field is the fallback for a hop that is
                // not a host of yours.
                listOf(
                    "host_list" to R.string.host_jump_mode_host_list,
                    "simple" to R.string.host_jump_mode_simple,
                ).forEach { (mode, labelRes) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        RadioButton(
                            selected = jumpMode == mode,
                            onClick  = { vm.jumpMode.value = mode },
                        )
                        Text(stringResource(labelRes))
                    }
                }
            }

            if (jumpMode == "simple") {
                HostField(
                    value = jumpHosts,
                    onValueChange = { vm.jumpHosts.value = it },
                    label = stringResource(R.string.host_field_jump_hosts),
                    touchless = touchless,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Uri,
                    placeholder = stringResource(R.string.host_jump_hosts_placeholder),
                    supporting = stringResource(R.string.host_jump_hosts_supporting),
                )
            } else {
                // Host-list mode: one row per hop
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    jumpHostIds.forEachIndexed { index, selectedId ->
                        JumpHostRow(
                            touchless        = touchless,
                            selectedId       = selectedId,
                            options          = availableJumpHosts,
                            // Everything past the first hop is dialled by the bastion's own ssh
                            // when that hop carries mosh, and a password saved here never
                            // reaches it. The first hop itself is ours to dial, so it is not
                            // held to the same rule.
                            requiresKey      = index > 0 && moshBastion != null,
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

            HostField(
                value = portForwardings,
                onValueChange = { vm.portForwardings.value = it },
                label = stringResource(R.string.host_field_port_forwarding),
                touchless = touchless,
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Uri,
                singleLine = false, minLines = 2,
                placeholder = stringResource(R.string.host_port_forwarding_placeholder),
                supporting = stringResource(R.string.host_port_forwarding_supporting),
            )

            HorizontalDivider()
            Text(stringResource(R.string.host_section_start_directory), style = MaterialTheme.typography.titleSmall)

            HostChoiceField(
                label = stringResource(R.string.host_start_mode_label),
                selected = sftpStartMode,
                options = listOf(
                    "last"  to R.string.host_start_mode_last,
                    "fixed" to R.string.host_start_mode_fixed,
                    "home"  to R.string.host_start_mode_home,
                ),
                onSelect = { vm.sftpStartMode.value = it },
                touchless = touchless,
                modifier = Modifier.fillMaxWidth(),
            )

            val startDirValue = when (sftpStartMode) {
                "home" -> "~"
                else   -> sftpStartDir
            }
            HostField(
                value = startDirValue,
                onValueChange = { if (sftpStartMode == "fixed") vm.sftpStartDir.value = it },
                label = stringResource(R.string.host_field_start_directory),
                touchless = touchless,
                modifier = Modifier.fillMaxWidth(),
                readOnly = sftpStartMode != "fixed",
                enabled = sftpStartMode == "fixed",
                keyboardType = KeyboardType.Uri,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = sftpShowHidden, onCheckedChange = { vm.sftpShowHidden.value = it })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.host_sftp_show_hidden))
            }

            if (hasStoredHostKeys) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { vm.resetHostKeys.value = !resetHostKeys },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(
                        if (resetHostKeys) R.string.host_reset_host_keys_pending
                        else R.string.host_reset_host_keys,
                    ))
                }
                Text(
                    stringResource(R.string.host_reset_host_keys_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }

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

    if (showHostColorDialog) {
        HostColorDialog(
            initialColor = hostColor,
            onConfirm    = { color ->
                vm.hostColor.value = color
                showHostColorDialog = false
            },
            onDismiss    = { showHostColorDialog = false },
        )
    }

    if (showNewGroupDialog) {
        GroupDialog(
            title        = stringResource(R.string.group_dialog_title_new),
            initialName  = "",
            // Default to the first swatch no existing group uses yet
            initialColor = GroupEntity.SWATCHES.firstOrNull { c -> groups.none { it.color == c } }
                ?: GroupEntity.SWATCHES.first(),
            onConfirm    = { name, color ->
                vm.createGroup(name, color)
                showNewGroupDialog = false
            },
            onDismiss    = { showNewGroupDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JumpHostRow(
    touchless: Boolean,
    selectedId: Long,
    options: List<AddEditHostViewModel.JumpHostOption>,
    /** True where a saved password is not enough, because this hop is dialled by the bastion. */
    requiresKey: Boolean,
    onSelect: (Long) -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.find { it.host.id == selectedId }
    val jumpItems: @Composable (dismiss: () -> Unit) -> Unit = { dismiss ->
        options.forEach { option ->
            val usable = option.hasCredentials && (!requiresKey || option.hasKey)
            // Two different refusals, and saying which one it is matters: one is cured by saving
            // a password, the other only by a key, and a host that has a password already would
            // otherwise be greyed out with a line telling it to save one.
            val whyNot = when {
                !option.hasCredentials -> R.string.host_jump_no_password
                !option.hasKey -> R.string.host_jump_needs_key
                else -> null
            }
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            option.host.label,
                            color = if (usable) LocalContentColor.current
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                        if (!usable && whyNot != null) {
                            Text(
                                stringResource(whyNot),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            )
                        }
                    }
                },
                onClick = {
                    if (usable) {
                        onSelect(option.host.id)
                        dismiss()
                    }
                },
                enabled = usable,
            )
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (touchless) {
            TvSelectField(
                label = stringResource(R.string.host_section_jump_hosts),
                valueText = selected?.host?.label
                    ?: stringResource(R.string.host_jump_select_placeholder),
                modifier = Modifier.weight(1f),
                showLabel = false,
                menuItems = jumpItems,
            )
        } else {
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
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    singleLine = true,
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    jumpItems { expanded = false }
                }
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Remove, contentDescription = null,
                tint = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * One of a few named choices, as a dropdown rather than a row of radio buttons.
 *
 * Radios were honest but they cost a line each and made the form long enough to hide what came
 * after them; three of these used to take eleven lines between them. The dropdown shows the
 * choice made, which is the only one anybody reads after the first time.
 *
 * Two flavours because the form already has two: a television has no touch, so there it is the
 * tap-to-open field the rest of the screen uses ([TvSelectField]) rather than a menu anchored to
 * a text box the remote cannot aim at.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HostChoiceField(
    label: String,
    selected: String,
    options: List<Pair<String, Int>>,
    onSelect: (String) -> Unit,
    touchless: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val shown = stringResource(options.firstOrNull { it.first == selected }?.second ?: options.first().second)
    val items: @Composable (dismiss: () -> Unit) -> Unit = { dismiss ->
        options.forEach { (value, labelRes) ->
            DropdownMenuItem(
                text = { Text(stringResource(labelRes)) },
                onClick = { onSelect(value); dismiss() },
            )
        }
    }
    if (touchless) {
        TvSelectField(label = label, valueText = shown, modifier = modifier, menuItems = items)
    } else {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = shown,
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                modifier = modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                singleLine = true,
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                items { expanded = false }
            }
        }
    }
}

/**
 * A host form text field. On a touchscreen it is the usual inline [OutlinedTextField].
 * On a touchless device (TV/D-pad) — where a focused text field would open the on-screen
 * keyboard and trap the focus, so ↑/↓ can no longer move between fields — it instead
 * renders a focusable "tap to edit" row ([TvTapField]) that opens the keyboard only on OK.
 * Read-only/disabled fields keep the inline control in both modes.
 */
@Composable
private fun HostField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    touchless: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    isPassword: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String? = null,
    supporting: String? = null,
) {
    if (touchless && enabled && !readOnly) {
        TvTapField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = modifier,
            keyboardType = keyboardType,
            isPassword = isPassword,
            singleLine = singleLine,
            placeholder = placeholder,
            supporting = supporting,
        )
        return
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = keyboardActions,
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supporting?.let { { Text(it) } },
    )
}

