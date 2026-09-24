package com.sshborg.ui.keys

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import android.content.ClipData
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sshborg.R
import com.sshborg.data.db.SshKeyEntity
import com.sshborg.isTouchless
import com.sshborg.ui.common.ScrollingDialogBody
import com.sshborg.ui.common.TvTapField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeysScreen(onBack: () -> Unit, vm: KeysViewModel = viewModel()) {
    val keys by vm.keys.collectAsState()
    var showGenerateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importPem by remember { mutableStateOf("") }
    var keyToDelete by remember { mutableStateOf<SshKeyEntity?>(null) }
    var keyToRename by remember { mutableStateOf<SshKeyEntity?>(null) }
    var expandedKeyId by remember { mutableStateOf<Long?>(null) }

    // Which keys cannot be used as they stand: encrypted, with no passphrase stored. Keys
    // imported before the app kept the passphrase are all in here, and nothing else would tell
    // them apart from a working key, so the list says so rather than letting the next connection
    // fail for no visible reason. Recomputed whenever the list changes, which is how the warning
    // goes away once the key is imported again.
    val needsPassphrase = remember { mutableStateMapOf<Long, Boolean>() }
    LaunchedEffect(keys) {
        keys.forEach { key -> needsPassphrase[key.id] = vm.needsPassphrase(key) }
    }

    val context = LocalContext.current
    val importFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            importPem = context.contentResolver.openInputStream(uri)
                ?.use { it.reader().readText() } ?: ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.keys_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SmallFloatingActionButton(onClick = { importPem = ""; showImportDialog = true }) {
                    Icon(Icons.Default.FileOpen, stringResource(R.string.keys_import_cd))
                }
                FloatingActionButton(onClick = { showGenerateDialog = true }) {
                    Icon(Icons.Default.Add, stringResource(R.string.keys_generate_cd))
                }
            }
        },
    ) { padding ->
        if (keys.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.keys_empty), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(keys, key = { it.id }) { key ->
                    KeyItem(
                        key = key,
                        expanded = expandedKeyId == key.id,
                        needsPassphrase = needsPassphrase[key.id] == true,
                        onExpand = { expandedKeyId = if (expandedKeyId == key.id) null else key.id },
                        onRename = { keyToRename = key },
                        onDelete = { keyToDelete = key },
                    )
                }
            }
        }
    }

    if (showImportDialog) {
        ImportKeyDialog(
            pem = importPem,
            onPemChange = { importPem = it },
            onLoadFromFile = { (context.applicationContext as com.sshborg.SshBorgApp).allowPickerTrip(); importFileLauncher.launch("*/*") },
            onImport = { label, pem, passphrase, onError ->
                vm.importKey(label, pem, passphrase, onError) { showImportDialog = false }
            },
            onDismiss = { showImportDialog = false },
        )
    }

    if (showGenerateDialog) {
        GenerateKeyDialog(
            onGenerate = { label, type, size -> vm.generateKey(label, type, size) {}; showGenerateDialog = false },
            onDismiss = { showGenerateDialog = false },
        )
    }

    keyToRename?.let { key ->
        RenameKeyDialog(
            currentLabel = key.label,
            onRename = { newLabel -> vm.renameKey(key, newLabel); keyToRename = null },
            onDismiss = { keyToRename = null },
        )
    }

    keyToDelete?.let { key ->
        AlertDialog(
            onDismissRequest = { keyToDelete = null },
            title = { Text(stringResource(R.string.keys_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.keys_delete_message, key.label))
                    Text(stringResource(R.string.keys_delete_warning))
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = { vm.deleteKey(key); keyToDelete = null },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error)),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { keyToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun KeyItem(
    key: SshKeyEntity,
    expanded: Boolean,
    /** The key is encrypted and its passphrase is not stored, so it cannot authenticate. */
    needsPassphrase: Boolean,
    onExpand: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    Column {
        ListItem(
            headlineContent = { Text(key.label) },
            supportingContent = {
                Column {
                    Text(
                        key.keyType,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                    // Nothing can be done about it from here — the passphrase is only ever
                    // taken at import — so this says what to do rather than offering a fix.
                    if (needsPassphrase) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                null,
                                Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.keys_needs_passphrase),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            },
            leadingContent = { Icon(Icons.Default.Key, null) },
            trailingContent = {
                Row {
                    IconButton(onClick = onExpand) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            stringResource(R.string.keys_show_public_key_cd)
                        )
                    }
                    IconButton(onClick = onRename) {
                        Icon(Icons.Default.Edit, stringResource(R.string.keys_rename_cd))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, stringResource(R.string.keys_delete_cd))
                    }
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
                    Text(
                        stringResource(R.string.keys_public_key_label),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        key.publicKey,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", key.publicKey)))
                            }
                            copied = true
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (copied) stringResource(R.string.action_copied)
                            else stringResource(R.string.action_copy)
                        )
                    }
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp)
    }
}

@Composable
private fun RenameKeyDialog(
    currentLabel: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf(currentLabel) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        // Short, but centred in the window it would still meet the keyboard; see the import
        // dialog for why the padding goes on the dialog and not on its content.
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text(stringResource(R.string.keys_rename_title)) },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.keygen_field_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (label.isNotBlank()) onRename(label.trim())
                    }
                ),
            )
        },
        confirmButton = {
            OutlinedButton(
                onClick = { focusManager.clearFocus(); if (label.isNotBlank()) onRename(label.trim()) },
                enabled = label.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ImportKeyDialog(
    pem: String,
    onPemChange: (String) -> Unit,
    onLoadFromFile: () -> Unit,
    onImport: (label: String, pem: String, passphrase: String?, onError: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var passphraseVisible by remember { mutableStateOf(false) }
    // One message at a time, kept against the field it is about: a bad passphrase is not the
    // key text's fault, and a message at the foot of a dialog that scrolls is a message nobody
    // sees. Whichever is set also turns its own field red.
    var pemError by remember { mutableStateOf("") }
    var passphraseError by remember { mutableStateOf("") }
    val clearErrors = { pemError = ""; passphraseError = "" }
    val importContext = LocalContext.current
    val touchless = remember { isTouchless(importContext) }
    val errorEncrypted   = stringResource(R.string.keys_import_error_encrypted)
    val errorWrongPass   = stringResource(R.string.keys_import_error_wrong_passphrase)
    val errorInvalid     = stringResource(R.string.keys_import_error_invalid)
    val defaultLabel     = stringResource(R.string.keys_import_default_label)

    AlertDialog(
        onDismissRequest = onDismiss,
        // With the keyboard up, this dialog is taller than the room left for it. The window must
        // not try to fit the IME itself — it keeps its full height and leaves the buttons behind
        // the keyboard. Padding the dialog's own content instead shrinks the space it is measured
        // in, so all of it, buttons included, is laid out above the keyboard and the body scrolls
        // for the rest. Hence no height cap on the body: the space left is the cap.
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text(stringResource(R.string.keys_import_title)) },
        text = {
            ScrollingDialogBody(
                maxHeight = Dp.Infinity,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (touchless) {
                    TvTapField(
                        value = label,
                        onValueChange = { label = it; clearErrors() },
                        label = stringResource(R.string.keygen_field_label),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it; clearErrors() },
                        label = { Text(stringResource(R.string.keygen_field_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedButton(
                    onClick = onLoadFromFile,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.FileOpen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.keys_import_load_from_file))
                }
                if (touchless) {
                    TvTapField(
                        value = pem,
                        onValueChange = { onPemChange(it); clearErrors() },
                        label = stringResource(R.string.keys_import_pem_label),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        supporting = pemError.takeIf { it.isNotEmpty() },
                        isError = pemError.isNotEmpty(),
                    )
                } else {
                    OutlinedTextField(
                        value = pem,
                        onValueChange = { onPemChange(it); clearErrors() },
                        label = { Text(stringResource(R.string.keys_import_pem_label)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace),
                        maxLines = 8,
                        isError = pemError.isNotEmpty(),
                        supportingText = if (pemError.isEmpty()) null else {
                            { Text(pemError) }
                        },
                    )
                }
                if (touchless) {
                    TvTapField(
                        value = passphrase,
                        onValueChange = { passphrase = it; clearErrors() },
                        label = stringResource(R.string.keys_import_passphrase_label),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardType = KeyboardType.Password,
                        isPassword = true,
                        supporting = passphraseError.takeIf { it.isNotEmpty() },
                        isError = passphraseError.isNotEmpty(),
                    )
                } else {
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it; clearErrors() },
                        label = { Text(stringResource(R.string.keys_import_passphrase_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = passphraseError.isNotEmpty(),
                        supportingText = if (passphraseError.isEmpty()) null else {
                            { Text(passphraseError) }
                        },
                        trailingIcon = {
                            TextButton(onClick = { passphraseVisible = !passphraseVisible }) {
                                Text(
                                    if (passphraseVisible) stringResource(R.string.action_hide)
                                    else stringResource(R.string.action_show)
                                )
                            }
                        },
                    )
                }
                Text(
                    stringResource(R.string.keys_passphrase_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = {
                    onImport(
                        label.ifBlank { defaultLabel },
                        pem,
                        passphrase.takeIf { it.isNotEmpty() },
                    ) { rawError ->
                        clearErrors()
                        when (rawError) {
                            "encrypted"        -> passphraseError = errorEncrypted
                            "wrong_passphrase" -> passphraseError = errorWrongPass
                            else               -> pemError = errorInvalid
                        }
                    }
                },
                enabled = pem.isNotBlank(),
            ) { Text(stringResource(R.string.keys_import_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun GenerateKeyDialog(onGenerate: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("ed25519") }
    var rsaBits by remember { mutableStateOf("4096") }
    var ecdsaCurve by remember { mutableStateOf("256") }
    val types = listOf("ed25519", "rsa", "ecdsa")
    val ecdsaCurves = listOf("256", "384", "521")
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val genContext = LocalContext.current
    val touchless = remember { isTouchless(genContext) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text(stringResource(R.string.keygen_title)) },
        text = {
            ScrollingDialogBody(
                maxHeight = Dp.Infinity,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (touchless) {
                    TvTapField(
                        value = label,
                        onValueChange = { label = it },
                        label = stringResource(R.string.keygen_field_label),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = label, onValueChange = { label = it },
                        label = { Text(stringResource(R.string.keygen_field_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Text(stringResource(R.string.keygen_key_type), style = MaterialTheme.typography.labelMedium)
                types.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = type == t, onClick = { type = t })
                        Spacer(Modifier.width(4.dp))
                        Text(t.uppercase())
                    }
                }

                when (type) {
                    "ed25519" -> Text(
                        stringResource(R.string.keygen_fixed_size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    "rsa" -> if (touchless) {
                        TvTapField(
                            value = rsaBits,
                            onValueChange = { if (it.all(Char::isDigit)) rsaBits = it },
                            label = stringResource(R.string.keygen_size_bits),
                            modifier = Modifier.fillMaxWidth(),
                            keyboardType = KeyboardType.Number,
                        )
                    } else {
                        OutlinedTextField(
                            value = rsaBits,
                            onValueChange = { if (it.all(Char::isDigit)) rsaBits = it },
                            label = { Text(stringResource(R.string.keygen_size_bits)) },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    "ecdsa" -> {
                        Text(stringResource(R.string.keygen_curve_bits), style = MaterialTheme.typography.labelMedium)
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
            OutlinedButton(
                onClick = { focusManager.clearFocus(); onGenerate(label.ifBlank { type.uppercase() + " Key" }, type, size) },
                enabled = type != "rsa" || rsaBits.toIntOrNull()?.let { it >= 1024 } == true,
            ) { Text(stringResource(R.string.action_generate)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
