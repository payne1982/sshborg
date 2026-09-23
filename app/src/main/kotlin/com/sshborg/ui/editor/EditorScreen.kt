package com.sshborg.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.sshborg.R

/**
 * The text editor for one remote file, shown over the file list.
 *
 * The text lives here rather than in the ViewModel: a state flow updated on every keystroke
 * would recompose the whole SFTP screen, and [rememberSaveable] gives the draft the one thing
 * that matters for free — it is written into the saved instance state, so a process killed
 * while the user is in another app does not cost them their edits. Files too big for a Bundle
 * are not kept; see [EDITOR_MAX_DRAFT].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: EditorState.Ready,
    onSave: (String) -> Unit,
    onClose: () -> Unit,
    charsets: () -> List<java.nio.charset.Charset> = ::emptyList,
    onCharset: (java.nio.charset.Charset) -> Unit = {},
    onDismissProblem: () -> Unit = {},
) {
    // Keyed on the charset too: reading the file another way replaces the text, which is the
    // point of changing it — the user only does so because what they are looking at is wrong.
    var draft by rememberSaveable(state.path, state.decoded.charset.name(), stateSaver = DraftSaver) {
        mutableStateOf(state.decoded.text)
    }
    // A save makes the server's copy match the text, whatever the baseline was before.
    val dirty = draft != state.decoded.text
    var confirmDiscard by rememberSaveable(state.path) { mutableStateOf(false) }
    var showCharsets by rememberSaveable(state.path) { mutableStateOf(false) }
    var confirmCharset by rememberSaveable(state.path) { mutableStateOf(false) }

    val back = { if (dirty) confirmDiscard = true else onClose() }
    BackHandler(onBack = back)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.name, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                        Text(
                            state.path,
                            maxLines = 1,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.saving) {
                        CircularProgressIndicator(Modifier.padding(horizontal = 16.dp).size(20.dp))
                    } else {
                        IconButton(onClick = { onSave(draft) }, enabled = dirty) {
                            Icon(Icons.Default.Check, stringResource(R.string.action_save))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            TextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth().weight(1f),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                // A configuration file is not prose: no capitals, no autocorrect, no spell check.
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
            StatusLine(
                state = state,
                dirty = dirty,
                onPickCharset = { if (dirty) confirmCharset = true else showCharsets = true },
            )
        }
    }

    if (showCharsets) {
        CharsetDialog(
            charsets = charsets(),
            current = state.decoded.charset,
            onPick = { showCharsets = false; onCharset(it) },
            onDismiss = { showCharsets = false },
        )
    }

    if (confirmCharset) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmCharset = false },
            title = { Text(stringResource(R.string.editor_charset)) },
            text = { Text(stringResource(R.string.editor_charset_discard)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmCharset = false; showCharsets = true }) {
                    Text(stringResource(R.string.editor_discard))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmCharset = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    state.problem?.let { problem ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismissProblem,
            icon = { Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.editor_save_failed)) },
            text = { Text(problem.message) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = onDismissProblem) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }

    if (confirmDiscard) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.editor_unsaved_title)) },
            text = { Text(stringResource(R.string.editor_unsaved_message)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDiscard = false; onClose() }) {
                    Text(stringResource(R.string.editor_discard))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDiscard = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * What the file is made of, so saving never holds a surprise: charset, line ending, state.
 * Tapping it picks another charset — the one judgement the machine cannot make, since the same
 * bytes are legal Cyrillic and legal Greek and only the reader can tell which one is words.
 */
@Composable
private fun StatusLine(state: EditorState.Ready, dirty: Boolean, onPickCharset: () -> Unit) {
    val parts = buildList {
        add(state.decoded.lineEnding.name)
        if (state.decoded.mixedEndings) add(stringResource(R.string.editor_mixed_endings))
        if (dirty) add(stringResource(R.string.editor_unsaved))
        else if (state.savedAt > 0L) add(stringResource(R.string.editor_saved))
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.TextButton(onClick = onPickCharset) {
            Text(state.decoded.label, style = MaterialTheme.typography.bodySmall)
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = stringResource(R.string.editor_charset),
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            parts.joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** While the file is being read. */
@Composable
fun EditorLoading(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text(name, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Drops a draft too big to travel in a Bundle instead of taking the app down with a
 * TransactionTooLargeException. Returning null from save means "do not keep this".
 */
private val DraftSaver: Saver<String, Any> = Saver(
    save = { if (it.length <= EDITOR_MAX_DRAFT) it else null },
    restore = { it as String },
)

/**
 * Why this file did not open: it is bigger than the editor holds, or it is not text at all.
 * Both point at the terminal, where the file can be edited on the server whatever its size —
 * until the hex view lands, that is the honest answer for a binary too.
 */
@Composable
fun EditorUnsupportedDialog(state: EditorState.Unsupported, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.name, maxLines = 1) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when (state.reason) {
                        EditorState.Reason.TOO_LARGE ->
                            stringResource(R.string.editor_too_large, formatBytes(state.size), formatBytes(EDITOR_MAX_BYTES))
                        EditorState.Reason.BINARY -> stringResource(R.string.editor_binary)
                    }
                )
                Text(
                    stringResource(R.string.editor_use_terminal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1_024     -> "$bytes B"
    bytes < 1_048_576 -> "%.1f KB".format(bytes / 1_024.0)
    else              -> "%.1f MB".format(bytes / 1_048_576.0)
}

/**
 * The charsets this file can be read as, best guess first. Only the ones whose round trip is
 * exact are here: anything else could not be saved back without changing bytes the user never
 * touched, so it is left out rather than offered as a trap.
 */
@Composable
private fun CharsetDialog(
    charsets: List<java.nio.charset.Charset>,
    current: java.nio.charset.Charset,
    onPick: (java.nio.charset.Charset) -> Unit,
    onDismiss: () -> Unit,
) {
    val maxBody = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.5f).dp
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_charset)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = maxBody)
                    .verticalScroll(rememberScrollState()),
            ) {
                charsets.forEach { charset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(charset) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = charset == current,
                            onClick = { onPick(charset) },
                        )
                        Text(charset.name(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
