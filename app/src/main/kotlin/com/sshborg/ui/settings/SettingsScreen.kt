package com.sshborg.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sshborg.BiometricHelper
import com.sshborg.R
import com.sshborg.data.AppPreferences
import com.sshborg.isTelevision
import com.sshborg.ui.lock.LockSecretDialog

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

// App-lock modes offered in the dropdown, in display order. "None" is always
// available; the two authentication modes need a device secure lock present.
private val LOCK_MODE_OPTIONS = listOf(
    AppPreferences.LOCK_NONE      to R.string.settings_lock_mode_none,
    AppPreferences.LOCK_BIOMETRIC to R.string.settings_lock_mode_biometric,
    AppPreferences.LOCK_DEVICE    to R.string.settings_lock_mode_device,
    AppPreferences.LOCK_SECRET    to R.string.settings_lock_mode_pin,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val lockMode              by vm.lockMode.collectAsState()
    val lockTimeoutSeconds    by vm.lockTimeoutSeconds.collectAsState()
    val keystoreEncryption    by vm.keystoreEncryption.collectAsState()
    val confirmExit           by vm.confirmExit.collectAsState()
    val invertTerminalScroll  by vm.invertTerminalScroll.collectAsState()
    val keepScreenOn          by vm.keepScreenOn.collectAsState()
    val terminalColorScheme   by vm.terminalColorScheme.collectAsState()
    val doubleTapAction       by vm.doubleTapAction.collectAsState()
    val historySuggestions    by vm.historySuggestions.collectAsState()
    val suggestionsBarSticky  by vm.suggestionsBarSticky.collectAsState()
    val extraKeysBarPinned    by vm.extraKeysBarPinned.collectAsState()
    val isMigrating           by vm.isMigrating.collectAsState()
    val nightMode             by vm.nightMode.collectAsState()
    val allowScreenshots      by vm.allowScreenshots.collectAsState()
    val scrollbackLines       by vm.scrollbackLines.collectAsState()
    val terminalFontSize      by vm.terminalFontSize.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val unknownError = stringResource(R.string.error_unknown)
    LaunchedEffect(Unit) {
        vm.error.collect { message ->
            val display = message.takeIf { it.isNotBlank() } ?: unknownError
            snackbarHostState.showSnackbar(display, duration = SnackbarDuration.Long)
        }
    }
    LaunchedEffect(Unit) {
        vm.message.collect { snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short) }
    }

    var showEnableEncryptionDialog by remember { mutableStateOf(false) }
    var timeoutMenuExpanded by remember { mutableStateOf(false) }
    var lockModeMenuExpanded by remember { mutableStateOf(false) }
    var showLockDisclaimer by remember { mutableStateOf(false) }
    var showLockSecretDialog by remember { mutableStateOf(false) }
    var lockDialogIsChange by remember { mutableStateOf(false) }
    var languageMenuExpanded by remember { mutableStateOf(false) }
    var themeMenuExpanded by remember { mutableStateOf(false) }
    var terminalColorsMenuExpanded by remember { mutableStateOf(false) }
    var doubleTapMenuExpanded by remember { mutableStateOf(false) }
    var scrollbackText by remember(scrollbackLines) { mutableStateOf(scrollbackLines.toString()) }
    var fontSizeText by remember(terminalFontSize) { mutableStateOf(terminalFontSize.toString()) }
    var currentLocaleTag by remember { mutableStateOf(vm.currentLocaleTag) }

    val biometricAvailable = remember { BiometricHelper.canAuthenticate(context) }
    val isTv = remember { isTelevision(context) }

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
                .verticalScroll(rememberScrollState())
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
                    "ru" to "Русский",
                    "zh" to "中文",
                    "ja" to "日本語",
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
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
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

            // Theme picker
            val themeOptions = listOf(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM to stringResource(R.string.settings_theme_follow_system),
                AppCompatDelegate.MODE_NIGHT_NO            to stringResource(R.string.settings_theme_light),
                AppCompatDelegate.MODE_NIGHT_YES           to stringResource(R.string.settings_theme_dark),
            )
            val currentThemeLabel = themeOptions.find { it.first == nightMode }?.second
                ?: stringResource(R.string.settings_theme_follow_system)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_theme_title)) },
                trailingContent = {
                    ExposedDropdownMenuBox(
                        expanded = themeMenuExpanded,
                        onExpandedChange = { themeMenuExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = currentThemeLabel,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(themeMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .width(180.dp),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            singleLine = true,
                        )
                        ExposedDropdownMenu(
                            expanded = themeMenuExpanded,
                            onDismissRequest = { themeMenuExpanded = false },
                        ) {
                            themeOptions.forEach { (mode, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        themeMenuExpanded = false
                                        vm.setNightMode(mode)
                                    },
                                )
                            }
                        }
                    }
                },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── Terminal section ──────────────────────────────────────────────
            Text(
                stringResource(R.string.settings_section_terminal),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Terminal color scheme picker
            val terminalColorOptions = listOf(
                AppPreferences.TERMINAL_SCHEME_DARK       to stringResource(R.string.settings_theme_dark),
                AppPreferences.TERMINAL_SCHEME_LIGHT      to stringResource(R.string.settings_theme_light),
                AppPreferences.TERMINAL_SCHEME_FOLLOW_APP to stringResource(R.string.settings_terminal_colors_follow_app),
            )
            val currentTerminalColorsLabel = terminalColorOptions.find { it.first == terminalColorScheme }?.second
                ?: terminalColorOptions.first().second
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_terminal_colors_title)) },
                trailingContent = {
                    ExposedDropdownMenuBox(
                        expanded = terminalColorsMenuExpanded,
                        onExpandedChange = { terminalColorsMenuExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = currentTerminalColorsLabel,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(terminalColorsMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .width(180.dp),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            singleLine = true,
                        )
                        ExposedDropdownMenu(
                            expanded = terminalColorsMenuExpanded,
                            onDismissRequest = { terminalColorsMenuExpanded = false },
                        ) {
                            terminalColorOptions.forEach { (scheme, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        terminalColorsMenuExpanded = false
                                        vm.setTerminalColorScheme(scheme)
                                    },
                                )
                            }
                        }
                    }
                },
            )

            // Double-tap action picker
            val doubleTapOptions = listOf(
                AppPreferences.DOUBLE_TAP_NONE      to stringResource(R.string.settings_double_tap_none),
                AppPreferences.DOUBLE_TAP_TAB       to stringResource(R.string.settings_double_tap_tab),
                AppPreferences.DOUBLE_TAP_TAB_TWICE to stringResource(R.string.settings_double_tap_tab_twice),
            )
            val currentDoubleTapLabel = doubleTapOptions.find { it.first == doubleTapAction }?.second
                ?: doubleTapOptions.first().second
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_double_tap_title)) },
                supportingContent = { Text(stringResource(R.string.settings_double_tap_subtitle)) },
                trailingContent = {
                    ExposedDropdownMenuBox(
                        expanded = doubleTapMenuExpanded,
                        onExpandedChange = { doubleTapMenuExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = currentDoubleTapLabel,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(doubleTapMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .width(180.dp),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            singleLine = true,
                        )
                        ExposedDropdownMenu(
                            expanded = doubleTapMenuExpanded,
                            onDismissRequest = { doubleTapMenuExpanded = false },
                        ) {
                            doubleTapOptions.forEach { (action, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        doubleTapMenuExpanded = false
                                        vm.setDoubleTapAction(action)
                                    },
                                )
                            }
                        }
                    }
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

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_keep_screen_on_title)) },
                supportingContent = { Text(stringResource(R.string.settings_keep_screen_on_subtitle)) },
                trailingContent = {
                    Switch(
                        checked = keepScreenOn,
                        onCheckedChange = { vm.setKeepScreenOn(it) },
                    )
                },
            )

            val saveFontSize = {
                val n = fontSizeText.toIntOrNull()
                    ?.coerceIn(AppPreferences.MIN_TERMINAL_FONT_SIZE, AppPreferences.MAX_TERMINAL_FONT_SIZE)
                    ?: AppPreferences.DEFAULT_TERMINAL_FONT_SIZE
                fontSizeText = n.toString()
                vm.setTerminalFontSize(n)
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_font_size_title)) },
                supportingContent = { Text(stringResource(R.string.settings_font_size_subtitle)) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = fontSizeText,
                            onValueChange = { fontSizeText = it.filter { c -> c.isDigit() } },
                            modifier = Modifier.width(90.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { saveFontSize(); focusManager.clearFocus() }),
                        )
                        IconButton(onClick = saveFontSize) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    }
                },
            )

            val saveScrollback = {
                val n = scrollbackText.toIntOrNull()?.coerceIn(100, 50000) ?: 2000
                scrollbackText = n.toString()
                vm.setScrollbackLines(n)
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_scrollback_title)) },
                supportingContent = { Text(stringResource(R.string.settings_scrollback_subtitle)) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = scrollbackText,
                            onValueChange = { scrollbackText = it.filter { c -> c.isDigit() } },
                            modifier = Modifier.width(90.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { saveScrollback(); focusManager.clearFocus() }),
                        )
                        IconButton(onClick = saveScrollback) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    }
                },
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_history_suggestions_title)) },
                supportingContent = { Text(stringResource(R.string.settings_history_suggestions_subtitle)) },
                trailingContent = {
                    Switch(
                        checked = historySuggestions,
                        onCheckedChange = { vm.setHistorySuggestions(it) },
                    )
                },
            )

            if (historySuggestions) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_suggestions_bar_sticky_title)) },
                    supportingContent = { Text(stringResource(R.string.settings_suggestions_bar_sticky_subtitle)) },
                    trailingContent = {
                        Switch(
                            checked = suggestionsBarSticky,
                            onCheckedChange = { vm.setSuggestionsBarSticky(it) },
                        )
                    },
                )
            }

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_extra_keys_bar_title)) },
                supportingContent = { Text(stringResource(R.string.settings_extra_keys_bar_subtitle)) },
                trailingContent = {
                    Switch(
                        checked = extraKeysBarPinned,
                        onCheckedChange = { vm.setExtraKeysBarPinned(it) },
                    )
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

            // App lock
            val currentLockLabel = stringResource(
                LOCK_MODE_OPTIONS.find { it.first == lockMode }?.second
                    ?: R.string.settings_lock_mode_none
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_app_lock_title)) },
                supportingContent = {
                    Text(
                        if (biometricAvailable)
                            stringResource(R.string.settings_biometric_available)
                        else
                            stringResource(R.string.settings_biometric_unavailable)
                    )
                },
                trailingContent = {
                    ExposedDropdownMenuBox(
                        expanded = lockModeMenuExpanded,
                        onExpandedChange = { lockModeMenuExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = currentLockLabel,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(lockModeMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .width(160.dp),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            singleLine = true,
                        )
                        ExposedDropdownMenu(
                            expanded = lockModeMenuExpanded,
                            onDismissRequest = { lockModeMenuExpanded = false },
                        ) {
                            // Biometric/device modes need a device secure lock and are
                            // filtered out entirely on a TV, where they don't work; None and
                            // the in-app PIN/passphrase are always offered.
                            val lockOptions = if (isTv)
                                LOCK_MODE_OPTIONS.filter {
                                    it.first == AppPreferences.LOCK_NONE || it.first == AppPreferences.LOCK_SECRET
                                }
                            else LOCK_MODE_OPTIONS
                            lockOptions.forEach { (mode, labelResId) ->
                                val enabled = mode == AppPreferences.LOCK_NONE ||
                                    mode == AppPreferences.LOCK_SECRET || biometricAvailable
                                DropdownMenuItem(
                                    text = { Text(stringResource(labelResId)) },
                                    enabled = enabled,
                                    onClick = {
                                        lockModeMenuExpanded = false
                                        if (mode == AppPreferences.LOCK_SECRET) {
                                            // Warn about no-recovery first, then capture the secret.
                                            showLockDisclaimer = true
                                        } else {
                                            vm.setLockMode(mode)
                                        }
                                    },
                                )
                            }
                        }
                    }
                },
            )

            // Change the stored PIN/passphrase (only while that mode is active).
            if (lockMode == AppPreferences.LOCK_SECRET) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_change_secret)) },
                    trailingContent = {
                        OutlinedButton(onClick = { lockDialogIsChange = true; showLockSecretDialog = true }) {
                            Text(stringResource(R.string.action_change))
                        }
                    },
                )
            }

            if (showLockDisclaimer) {
                AlertDialog(
                    onDismissRequest = { showLockDisclaimer = false },
                    title = { Text(stringResource(R.string.lock_disclaimer_title)) },
                    text  = { Text(stringResource(R.string.lock_disclaimer_body)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showLockDisclaimer = false
                            lockDialogIsChange = false
                            showLockSecretDialog = true
                        }) { Text(stringResource(R.string.lock_disclaimer_continue)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLockDisclaimer = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    },
                )
            }

            if (showLockSecretDialog) {
                LockSecretDialog(
                    onDismiss = { showLockSecretDialog = false; lockDialogIsChange = false },
                    onConfirm = { kind, secret ->
                        vm.setAppLockSecret(kind, secret)
                        showLockSecretDialog = false
                        lockDialogIsChange = false
                    },
                    verifyCurrent = if (lockDialogIsChange) ({ vm.checkAppLockSecret(it) }) else null,
                )
            }

            // Lock timeout — only shown when a lock is enabled
            if (lockMode != AppPreferences.LOCK_NONE) {
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
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
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
                headlineContent = { Text(stringResource(R.string.settings_allow_screenshots_title)) },
                supportingContent = { Text(stringResource(R.string.settings_allow_screenshots_subtitle)) },
                trailingContent = {
                    Switch(
                        checked = allowScreenshots,
                        onCheckedChange = { vm.setAllowScreenshots(it) },
                    )
                },
            )

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

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── Backup section ────────────────────────────────────────────────
            Text(
                stringResource(R.string.settings_section_backup),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json")
            ) { uri -> if (uri != null) vm.exportHosts(uri) }

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_backup_export_title)) },
                supportingContent = { Text(stringResource(R.string.settings_backup_export_subtitle)) },
                trailingContent = {
                    OutlinedButton(onClick = { exportLauncher.launch("sshborg_backup.json") }) {
                        Text(stringResource(R.string.settings_backup_export_action))
                    }
                },
            )

            val importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri -> if (uri != null) vm.importHosts(uri) }

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_backup_import_title)) },
                supportingContent = { Text(stringResource(R.string.settings_backup_import_subtitle)) },
                trailingContent = {
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) {
                        Text(stringResource(R.string.settings_backup_import_action))
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
                OutlinedButton(onClick = {
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
