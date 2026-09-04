package com.sshborg.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.sshborg.R

/**
 * Touchless "tap to edit" field, for TV/D-pad devices. It is a focusable bordered row that
 * shows the current value but does NOT take an editing focus, so a D-pad can move past it
 * with ↑/↓ without the on-screen keyboard opening and trapping the focus. Pressing OK opens
 * a dialog with a real [OutlinedTextField] (auto-focused, so the keyboard appears there);
 * Done or the OK button closes it and fires [onDone].
 *
 * Edits go straight through [onValueChange] to the caller's state (same as an inline field),
 * so nothing extra is committed on its own — use [onDone] for callers that persist on commit
 * (e.g. a settings value saved when editing ends).
 *
 * Callers gate this on `isTouchless(context)` and keep the inline [OutlinedTextField] on touch.
 */
@Composable
fun TvTapField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    singleLine: Boolean = true,
    placeholder: String? = null,
    supporting: String? = null,
    onDone: () -> Unit = {},
) {
    var editing by remember { mutableStateOf(false) }
    val display = when {
        value.isEmpty() -> placeholder ?: ""
        isPassword -> "•".repeat(value.length.coerceAtMost(24))
        else -> value
    }

    Column(modifier) {
        Surface(
            onClick = { editing = true },
            shape = RoundedCornerShape(4.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                if (showLabel) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    display.ifEmpty { " " },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = if (singleLine) 1 else 3,
                    color = if (value.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (supporting != null) {
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
    }

    if (editing) {
        // The edit is buffered so Cancel/back can discard it; only OK (or keyboard Done)
        // writes it back through onValueChange and fires onDone.
        var draft by remember { mutableStateOf(value) }
        var reveal by remember { mutableStateOf(false) }
        val fr = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
        val commit = {
            onValueChange(draft)
            editing = false
            onDone()
        }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(label) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = singleLine,
                    minLines = if (singleLine) 1 else 2,
                    visualTransformation = if (isPassword && !reveal) PasswordVisualTransformation()
                                           else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
                    ),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    trailingIcon = if (isPassword) {
                        {
                            TextButton(onClick = { reveal = !reveal }) {
                                Text(
                                    if (reveal) stringResource(R.string.action_hide)
                                    else stringResource(R.string.action_show)
                                )
                            }
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth().focusRequester(fr),
                )
            },
            confirmButton = {
                TextButton(onClick = commit) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

/**
 * Touchless "tap to open" selector — the dropdown counterpart of [TvTapField]. An
 * [ExposedDropdownMenuBox] traps the D-pad focus on a TV (its anchor is a text field), so
 * here the anchor is a plain focusable row that a D-pad can move past with ↑/↓; pressing OK
 * opens a dialog listing the options. The caller emits the option rows via [menuItems]
 * (typically the same [DropdownMenuItem]s used in the touch [ExposedDropdownMenu]), calling
 * the supplied `dismiss` after acting. Callers gate this on `isTouchless(context)`.
 */
@Composable
fun TvSelectField(
    label: String,
    valueText: String,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    menuItems: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Surface(
        onClick = { open = true },
        shape = RoundedCornerShape(4.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            leadingIcon?.invoke()
            Column(Modifier.weight(1f)) {
                if (showLabel) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(valueText, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                // Capped so a long list (e.g. all languages) stays compact on a short
                // landscape TV screen — about four rows show, the rest scrolls with the
                // D-pad as focus moves down. Chevrons at the edges signal hidden rows.
                val scroll = rememberScrollState()
                Box {
                    Column(
                        Modifier
                            .heightIn(max = 216.dp)
                            .verticalScroll(scroll)
                    ) {
                        menuItems { open = false }
                    }
                    if (scroll.canScrollBackward) {
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                    if (scroll.canScrollForward) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}
