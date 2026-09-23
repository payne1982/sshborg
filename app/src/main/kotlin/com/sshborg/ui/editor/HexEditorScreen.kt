package com.sshborg.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sshborg.R

/** The dump's text style. A composable because the font is loaded from the app's own assets. */
@Composable
private fun hexStyle() = TextStyle(fontFamily = com.sshborg.ui.common.monoFont(), fontSize = 13.sp)

/**
 * The hex editor: offsets down the left, the bytes in the middle, the printable characters on
 * the right, and a keypad at the bottom because no soft keyboard offers A to F.
 *
 * Rows are a lazy list, so only the ones on screen exist and the file's size does not decide
 * how fast it feels — unlike the text editor, which has to measure everything it holds. Each
 * row is one piece of monospaced text; tapping it maps the character back to the byte under the
 * finger, which is both simpler and faster than a widget per byte.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HexEditorScreen(
    state: EditorState.Hex,
    buffer: HexBuffer,
    onSave: (ByteArray) -> Unit,
    onClose: () -> Unit,
    onDismissProblem: () -> Unit,
) {
    var selected by remember(state.path) { mutableIntStateOf(0) }
    // Which half of the byte the next digit typed goes into: high first, then low.
    var lowNibble by remember(state.path) { mutableIntStateOf(0) }
    var confirmDiscard by remember(state.path) { androidx.compose.runtime.mutableStateOf(false) }

    val listState = rememberLazyListState()
    val dirty = buffer.dirty

    val back = { if (dirty) confirmDiscard = true else onClose() }
    BackHandler(onBack = back)

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = {
                    Column {
                        Text(state.name, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.editor_hex_subtitle, state.size),
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
                    IconButton(
                        onClick = { buffer.undo()?.let { selected = it; lowNibble = 0 } },
                        enabled = dirty,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.action_undo))
                    }
                    if (state.saving) {
                        CircularProgressIndicator(Modifier.padding(horizontal = 16.dp).size(20.dp))
                    } else {
                        IconButton(onClick = { onSave(buffer.bytes.copyOf()) }, enabled = dirty) {
                            Icon(Icons.Default.Check, stringResource(R.string.action_save))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                // As many bytes per row as the width honestly fits: a row is 10 characters of
                // offset, three per byte in hex and one more per byte in the text column.
                val measurer = rememberTextMeasurer()
                val style = hexStyle()
                val charWidth = remember(measurer, style, maxWidth) {
                    measurer.measure(AnnotatedString("0"), style).size.width
                }
                val perRow = remember(charWidth, constraints.maxWidth) {
                    val fits = ((constraints.maxWidth / charWidth.toFloat()) - 11) / 4
                    when {
                        fits >= 16 -> 16
                        fits >= 8 -> 8
                        else -> 4
                    }
                }
                val rows = (buffer.size + perRow - 1) / perRow

                LaunchedEffect(selected, perRow) {
                    val row = selected / perRow
                    val visible = listState.layoutInfo.visibleItemsInfo
                    if (visible.isNotEmpty() && (row < visible.first().index || row > visible.last().index)) {
                        listState.scrollToItem(row)
                    }
                }

                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(rows) { row ->
                        HexRow(
                            buffer = buffer,
                            row = row,
                            perRow = perRow,
                            selected = selected,
                            onPick = { index -> selected = index; lowNibble = 0 },
                        )
                    }
                }
            }
            HexKeypad(
                onDigit = { digit ->
                    val current = buffer[selected].toInt() and 0xFF
                    if (lowNibble == 0) {
                        buffer.set(selected, ((digit shl 4) or (current and 0x0F)).toByte())
                        lowNibble = 1
                    } else {
                        buffer.set(selected, ((current and 0xF0) or digit).toByte())
                        lowNibble = 0
                        if (selected < buffer.size - 1) selected++
                    }
                },
                onLeft = { if (selected > 0) { selected--; lowNibble = 0 } },
                onRight = { if (selected < buffer.size - 1) { selected++; lowNibble = 0 } },
            )
        }
    }

    if (confirmDiscard) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.editor_unsaved_title)) },
            text = { Text(stringResource(R.string.editor_unsaved_message)) },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onClose() }) {
                    Text(stringResource(R.string.editor_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.action_cancel)) }
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
                TextButton(onClick = onDismissProblem) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
}

/** One row: `00000010  48 65 6c 6c  Hell`. The selected byte is highlighted in both columns. */
@Composable
private fun HexRow(
    buffer: HexBuffer,
    row: Int,
    perRow: Int,
    selected: Int,
    onPick: (Int) -> Unit,
) {
    val start = row * perRow
    val count = minOf(perRow, buffer.size - start)
    // Read so the row redraws when a byte changes; the value itself is not needed here.
    buffer.revision

    val highlight = SpanStyle(
        background = MaterialTheme.colorScheme.primary,
        color = MaterialTheme.colorScheme.onPrimary,
    )
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    val line = buildAnnotatedString {
        withStyle(SpanStyle(color = dim)) { append("%08X  ".format(start)) }
        for (i in 0 until count) {
            val hex = "%02X".format(buffer[start + i].toInt() and 0xFF)
            if (start + i == selected) withStyle(highlight) { append(hex) } else append(hex)
            append(" ")
        }
        repeat(perRow - count) { append("   ") }
        append(" ")
        for (i in 0 until count) {
            val b = buffer[start + i].toInt() and 0xFF
            val ch = if (b in 0x20..0x7E) b.toChar() else '.'
            if (start + i == selected) withStyle(highlight) { append(ch) } else append(ch)
        }
    }

    var layout by remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    Text(
        line,
        style = hexStyle(),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .pointerInput(perRow, count) {
                detectTapGestures { position ->
                    val result = layout ?: return@detectTapGestures
                    val offset = result.getOffsetForPosition(position)
                    byteAt(offset, perRow, count)?.let { onPick(start + it) }
                }
            },
    )
}

/**
 * Which byte of the row a character position belongs to, or null for the gaps between columns.
 * The row is 10 characters of offset, then three per byte, then a space, then one per byte.
 */
private fun byteAt(offset: Int, perRow: Int, count: Int): Int? {
    val hexStart = 10
    val hexEnd = hexStart + perRow * 3
    if (offset in hexStart until hexEnd) return ((offset - hexStart) / 3).takeIf { it < count }
    val textStart = hexEnd + 1
    if (offset >= textStart) return (offset - textStart).takeIf { it < count }
    return null
}

/** Sixteen digits and two arrows: what a soft keyboard cannot give and a D-pad can reach. */
@Composable
private fun HexKeypad(onDigit: (Int) -> Unit, onLeft: () -> Unit, onRight: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(0..7, 8..15).forEach { range ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                range.forEach { digit ->
                    com.sshborg.ui.common.FocusOutlinedButton(
                        onClick = { onDigit(digit) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("%X".format(digit), style = hexStyle())
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            com.sshborg.ui.common.FocusOutlinedButton(onClick = onLeft, modifier = Modifier.weight(1f)) {
                Text("◀", style = hexStyle())
            }
            com.sshborg.ui.common.FocusOutlinedButton(onClick = onRight, modifier = Modifier.weight(1f)) {
                Text("▶", style = hexStyle())
            }
        }
    }
}
