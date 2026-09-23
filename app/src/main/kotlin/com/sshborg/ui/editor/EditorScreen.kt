package com.sshborg.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.sshborg.R
import com.sshborg.ui.common.ScrollingDialogBody

/**
 * The text editor for one remote file, shown over the file list.
 *
 * The text lives here rather than in the ViewModel: a state flow updated on every keystroke
 * would recompose the whole SFTP screen, and [rememberSaveable] gives the draft the one thing
 * that matters for free — it is written into the saved instance state, so a process killed
 * while the user is in another app does not cost them their edits. Files too big for a Bundle
 * are not kept; see [EDITOR_MAX_DRAFT].
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    val text = rememberSaveable(state.path, state.decoded.charset.name(), saver = DraftSaver) {
        TextFieldState(state.decoded.text)
    }
    // Carried rather than worked out: comparing the whole text against the original on every
    // recomposition is one more pass over the file per keystroke, which is exactly what this
    // screen cannot afford. The edit callback sets it, a landed save clears it.
    var dirty by rememberSaveable(state.path, state.decoded.charset.name()) { mutableStateOf(false) }
    // Guarded on the change count: this also runs when only the cursor moves, and a cursor
    // moved is not a file edited.
    val markDirty = remember { InputTransformation { if (changes.changeCount > 0) dirty = true } }
    LaunchedEffect(state.savedAt) { if (state.savedAt > 0L) dirty = false }
    var confirmDiscard by rememberSaveable(state.path) { mutableStateOf(false) }
    var showCharsets by rememberSaveable(state.path) { mutableStateOf(false) }
    var confirmCharset by rememberSaveable(state.path) { mutableStateOf(false) }

    val back = { if (dirty && !state.readOnly) confirmDiscard = true else onClose() }
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
                    if (state.readOnly) Unit
                    else if (state.saving) {
                        CircularProgressIndicator(Modifier.padding(horizontal = 16.dp).size(20.dp))
                    } else {
                        IconButton(onClick = { onSave(text.text.toString()) }, enabled = dirty) {
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
            if (state.readOnly) {
                ReadOnlyText(state.decoded.text, Modifier.weight(1f))
            } else {
                // BasicTextField, not the Material one: this is a whole screen of monospace
                // text, so the decoration, the indicator and the label are all things to
                // switch off again.
                BasicTextField(
                    state = text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    lineLimits = TextFieldLineLimits.MultiLine(),
                    inputTransformation = markDirty,
                    // A configuration file is not prose: no capitals, no autocorrect, no spell check.
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                    ),
                )
            }
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
        if (state.readOnly) add(stringResource(R.string.editor_read_only))
        else if (dirty) add(stringResource(R.string.editor_unsaved))
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

/**
 * While the file is being read. With a size to go by it says how far it has got: a few
 * megabytes over a slow link take long enough that a spinner alone reads as a hung screen.
 */
@Composable
fun EditorLoading(state: EditorState.Loading) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            if (state.size > 0) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { (state.received.toFloat() / state.size).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                CircularProgressIndicator()
            }
            Text(state.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            if (state.size > 0) {
                Text(
                    "${formatBytes(state.received)} / ${formatBytes(state.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Keeps the text across a process death, and drops a draft too big to travel in a Bundle
 * instead of taking the app down with a TransactionTooLargeException — returning null from
 * save means "do not keep this". The cursor is not kept; the text is what matters.
 */
private val DraftSaver: Saver<TextFieldState, Any> = Saver(
    save = { it.text.takeIf { t -> t.length <= EDITOR_MAX_DRAFT }?.toString() },
    restore = { TextFieldState(it as String) },
)

/**
 * Why this file did not open: it is bigger than the editor holds, or it is not text at all.
 * Both point at the terminal, where the file can be edited on the server whatever its size —
 * until the hex view lands, that is the honest answer for a binary too.
 */
@Composable
fun EditorUnsupportedDialog(
    state: EditorState.Unsupported,
    onHex: () -> Unit,
    onAsText: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.name, maxLines = 1) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when (state.reason) {
                        EditorState.Reason.TOO_LARGE -> stringResource(
                            R.string.editor_too_large,
                            formatBytes(state.size),
                            formatBytes(EDITOR_VIEW_MAX_BYTES),
                        )
                        EditorState.Reason.BINARY -> stringResource(R.string.editor_binary)
                    }
                )
                // The terminal is the answer for a file too big for us, not for a binary:
                // that one has the hex editor now.
                if (state.reason == EditorState.Reason.TOO_LARGE) {
                    Text(
                        stringResource(R.string.editor_use_terminal),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                // Not text is not the end of the road any more: the bytes can still be opened.
                if (state.reason == EditorState.Reason.BINARY) {
                    androidx.compose.material3.TextButton(onClick = onHex) {
                        Text(stringResource(R.string.editor_open_hex))
                    }
                    // A text file with one stray NUL in it is still a text file. Opening it is
                    // safe whatever it really holds: the round trip has to be exact either way.
                    androidx.compose.material3.TextButton(onClick = onAsText) {
                        Text(stringResource(R.string.editor_open_as_text))
                    }
                }
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_close))
                }
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
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_charset)) },
        text = {
            ScrollingDialogBody {
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

/** Asked before reading a file big enough that the editor will feel slow. */
@Composable
fun EditorConfirmDialog(
    state: EditorState.Confirm,
    onOpen: () -> Unit,
    onView: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.name, maxLines = 1) },
        text = {
            Text(stringResource(
                if (state.canEdit) R.string.editor_large else R.string.editor_too_large_to_edit,
                formatBytes(state.size),
                formatBytes(EDITOR_MAX_BYTES),
            ))
        },
        // Two ways forward, so they share the confirm slot: reading a big file is fast whatever
        // its size, and it is what most people opening one actually want.
        // One per line: side by side they are long enough to wrap, and a wrapped button
        // label is the sort of thing that makes a dialog look broken.
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (state.canEdit) {
                    androidx.compose.material3.TextButton(onClick = onOpen) {
                        Text(stringResource(R.string.sftp_menu_open))
                    }
                }
                androidx.compose.material3.TextButton(onClick = onView) {
                    Text(stringResource(R.string.editor_read_only))
                }
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * A big file with nothing to type into: one line per row in a lazy list, so only the rows on
 * screen exist and a file of megabytes scrolls as smoothly as a short one. This is the whole
 * difference from the editor — a text field has to measure every line it holds, visible or not.
 *
 * Wrapped in a SelectionContainer so it can still be read out and copied, and the rows are
 * focusable on a touchless device, which is how a D-pad scrolls a list.
 */
@Composable
private fun ReadOnlyText(text: String, modifier: Modifier = Modifier) {
    val lines = remember(text) { text.split("\n") }
    val touchless = com.sshborg.isTouchless(androidx.compose.ui.platform.LocalContext.current)
    androidx.compose.foundation.text.selection.SelectionContainer(modifier) {
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth()) {
            items(lines.size) { i ->
                Text(
                    lines[i],
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .then(if (touchless) Modifier.focusable() else Modifier),
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
