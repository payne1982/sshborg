package com.sshborg.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sshborg.BiometricHelper

private val TIMEOUT_OPTIONS = listOf(
    0    to "Immediately",
    30   to "30 seconds",
    60   to "1 minute",
    180  to "3 minutes",
    300  to "5 minutes",
    900  to "15 minutes",
    1800 to "30 minutes",
    3600 to "1 hour",
    14400 to "4 hours",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val biometricLock         by vm.biometricLock.collectAsState()
    val lockTimeoutSeconds    by vm.lockTimeoutSeconds.collectAsState()
    val keystoreEncryption    by vm.keystoreEncryption.collectAsState()
    val confirmExit           by vm.confirmExit.collectAsState()
    val invertTerminalScroll  by vm.invertTerminalScroll.collectAsState()
    val isMigrating           by vm.isMigrating.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.error.collect { snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long) }
    }

    var showEnableEncryptionDialog by remember { mutableStateOf(false) }
    var showDisableEncryptionDialog by remember { mutableStateOf(false) }
    var timeoutMenuExpanded by remember { mutableStateOf(false) }

    val biometricAvailable = remember { BiometricHelper.canAuthenticate(context) }
    val currentTimeoutLabel = TIMEOUT_OPTIONS.find { it.first == lockTimeoutSeconds }?.second
        ?: "$lockTimeoutSeconds seconds"

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(vertical = 8.dp),
        ) {
            // ── General section ───────────────────────────────────────────────
            Text(
                "General",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            ListItem(
                headlineContent = { Text("Confirm exit") },
                supportingContent = { Text("Ask for confirmation before closing the app") },
                trailingContent = {
                    Switch(
                        checked = confirmExit,
                        onCheckedChange = { vm.setConfirmExit(it) },
                    )
                },
            )

            ListItem(
                headlineContent = { Text("Invert terminal scroll") },
                supportingContent = { Text("Swipe up to see newer output instead of older") },
                trailingContent = {
                    Switch(
                        checked = invertTerminalScroll,
                        onCheckedChange = { vm.setInvertTerminalScroll(it) },
                    )
                },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── Security section ──────────────────────────────────────────────
            Text(
                "Security",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Biometric lock
            ListItem(
                headlineContent = { Text("Biometric lock") },
                supportingContent = {
                    Text(
                        if (biometricAvailable)
                            "Require authentication when returning to the app"
                        else
                            "No biometric or screen lock set up on this device"
                    )
                },
                trailingContent = {
                    Switch(
                        checked = biometricLock,
                        onCheckedChange = { vm.setBiometricLock(it) },
                        enabled = biometricAvailable,
                    )
                },
            )

            // Lock timeout — only shown when biometric is enabled
            if (biometricLock) {
                ListItem(
                    headlineContent = { Text("Lock after") },
                    supportingContent = { Text("Time before requiring authentication again") },
                    trailingContent = {
                        ExposedDropdownMenuBox(
                            expanded = timeoutMenuExpanded,
                            onExpandedChange = { timeoutMenuExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = currentTimeoutLabel,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(timeoutMenuExpanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .width(160.dp),
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                singleLine = true,
                            )
                            ExposedDropdownMenu(
                                expanded = timeoutMenuExpanded,
                                onDismissRequest = { timeoutMenuExpanded = false },
                            ) {
                                TIMEOUT_OPTIONS.forEach { (seconds, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            vm.setLockTimeoutSeconds(seconds)
                                            timeoutMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Keystore encryption
            ListItem(
                headlineContent = { Text("Encrypt sensitive data") },
                supportingContent = {
                    Text(
                        "SSH private keys and host passwords are encrypted using this device's secure hardware. " +
                        "If you uninstall the app, encrypted keys will become inaccessible."
                    )
                },
                trailingContent = {
                    if (isMigrating) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Switch(
                            checked = keystoreEncryption,
                            onCheckedChange = { enabled ->
                                if (enabled) showEnableEncryptionDialog = true
                                else showDisableEncryptionDialog = true
                            },
                        )
                    }
                },
            )
        }
    }

    if (showDisableEncryptionDialog) {
        AlertDialog(
            onDismissRequest = { showDisableEncryptionDialog = false },
            title = { Text("Disable encryption?") },
            text  = {
                Text(
                    "SSH private keys will be decrypted and stored in plain text.\n\n" +
                    "Saved host passwords will be removed — you will be asked to enter them again on the next connection."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDisableEncryptionDialog = false
                    vm.disableKeystoreEncryption()
                }) { Text("Disable") }
            },
            dismissButton = {
                TextButton(onClick = { showDisableEncryptionDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showEnableEncryptionDialog) {
        AlertDialog(
            onDismissRequest = { showEnableEncryptionDialog = false },
            title = { Text("Encrypt sensitive data?") },
            text  = {
                Text(
                    "Your SSH private keys and host passwords will be encrypted using this device's secure hardware.\n\n" +
                    "Encrypted data is bound to this device and app installation. " +
                    "If you uninstall the app, your current keys will become inaccessible and " +
                    "you will need to generate new ones and add them to your servers again."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showEnableEncryptionDialog = false
                    vm.enableKeystoreEncryption()
                }) { Text("Enable") }
            },
            dismissButton = {
                TextButton(onClick = { showEnableEncryptionDialog = false }) { Text("Cancel") }
            },
        )
    }
}
