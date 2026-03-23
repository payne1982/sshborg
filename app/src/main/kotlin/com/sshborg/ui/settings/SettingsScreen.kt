package com.sshborg.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sshborg.BiometricHelper
import com.sshborg.R

private val TIMEOUT_OPTIONS = listOf(
    0     to R.string.timeout_immediately,
    30    to R.string.timeout_30_seconds,
    60    to R.string.timeout_1_minute,
    180   to R.string.timeout_3_minutes,
    300   to R.string.timeout_5_minutes,
    900   to R.string.timeout_15_minutes,
    1800  to R.string.timeout_30_minutes,
    3600  to R.string.timeout_1_hour,
    14400 to R.string.timeout_4_hours,
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
    var timeoutMenuExpanded by remember { mutableStateOf(false) }
    var languageMenuExpanded by remember { mutableStateOf(false) }
    var currentLocaleTag by remember { mutableStateOf(vm.currentLocaleTag) }

    val biometricAvailable = remember { BiometricHelper.canAuthenticate(context) }

    // Map seconds to string resource id, then resolve the label
    val currentTimeoutResId = TIMEOUT_OPTIONS.find { it.first == lockTimeoutSeconds }?.second
    val currentTimeoutLabel = if (currentTimeoutResId != null)
        stringResource(currentTimeoutResId)
    else
        stringResource(R.string.settings_lock_timeout_fallback, lockTimeoutSeconds)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
                .padding(vertical = 8.dp),
        ) {
            // ── General section ───────────────────────────────────────────────
            Text(
                stringResource(R.string.settings_section_general),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_confirm_exit_title)) },
                supportingContent = { Text(stringResource(R.string.settings_confirm_exit_subtitle)) },
                trailingContent = {
                    Switch(
                        checked = confirmExit,
                        onCheckedChange = { vm.setConfirmExit(it) },
                    )
                },
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_invert_scroll_title)) },
                supportingContent = { Text(stringResource(R.string.settings_invert_scroll_subtitle)) },
                trailingContent = {
                    Switch(
                        checked = invertTerminalScroll,
                        onCheckedChange = { vm.setInvertTerminalScroll(it) },
                    )
                },
            )

            // Language picker
            val systemDefaultLabel = stringResource(R.string.settings_language_system)
            val languageOptions = remember {
                listOf(
                    "" to systemDefaultLabel,
                    "en" to "English",
                    "it" to "Italiano",
                    "fr" to "Français",
                    "de" to "Deutsch",
                    "es" to "Español",
                    "pt" to "Português",
                    "uk" to "Українська",
                )
            }
            val currentLanguageLabel = languageOptions.find { it.first == currentLocaleTag }?.second
                ?: systemDefaultLabel
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_language)) },
                trailingContent = {
                    ExposedDropdownMenuBox(
                        expanded = languageMenuExpanded,
                        onExpandedChange = { languageMenuExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = currentLanguageLabel,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(languageMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .width(180.dp),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            singleLine = true,
                        )
                        ExposedDropdownMenu(
                            expanded = languageMenuExpanded,
                            onDismissRequest = { languageMenuExpanded = false },
                        ) {
                            languageOptions.forEach { (tag, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        languageMenuExpanded = false
                                        currentLocaleTag = tag
                                        vm.setLocale(tag)
                                    },
                                )
                            }
                        }
                    }
                },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── Security section ──────────────────────────────────────────────
            Text(
                stringResource(R.string.settings_section_security),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Biometric lock
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_biometric_lock_title)) },
                supportingContent = {
                    Text(
                        if (biometricAvailable)
                            stringResource(R.string.settings_biometric_available)
                        else
                            stringResource(R.string.settings_biometric_unavailable)
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
                    headlineContent = { Text(stringResource(R.string.settings_lock_after_title)) },
                    supportingContent = { Text(stringResource(R.string.settings_lock_after_subtitle)) },
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
                                TIMEOUT_OPTIONS.forEach { (seconds, labelResId) ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(labelResId)) },
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
                headlineContent = { Text(stringResource(R.string.settings_encrypt_title)) },
                supportingContent = { Text(stringResource(R.string.settings_encrypt_subtitle)) },
                trailingContent = {
                    if (isMigrating) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Switch(
                            checked = keystoreEncryption,
                            onCheckedChange = { enabled ->
                                if (enabled) showEnableEncryptionDialog = true
                                else vm.disableKeystoreEncryption()
                            },
                        )
                    }
                },
            )
        }
    }

    if (showEnableEncryptionDialog) {
        AlertDialog(
            onDismissRequest = { showEnableEncryptionDialog = false },
            title = { Text(stringResource(R.string.settings_encrypt_dialog_title)) },
            text  = { Text(stringResource(R.string.settings_encrypt_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showEnableEncryptionDialog = false
                    vm.enableKeystoreEncryption()
                }) { Text(stringResource(R.string.action_enable)) }
            },
            dismissButton = {
                TextButton(onClick = { showEnableEncryptionDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
