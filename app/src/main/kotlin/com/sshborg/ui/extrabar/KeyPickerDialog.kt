package com.sshborg.ui.extrabar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.sshborg.R
import com.sshborg.data.BarAction
import com.sshborg.data.ExtraKeyDef
import com.sshborg.data.ModKey
import com.sshborg.data.SpecialKey
import com.sshborg.isTouchless
import com.sshborg.ui.common.TvTapField

/** Localised name of an action key, for the catalogue chips. */
@Composable
fun barActionName(action: BarAction): String = stringResource(
    when (action) {
        BarAction.PASTE      -> R.string.extra_key_action_paste
        BarAction.PIN        -> R.string.extra_key_action_pin
        BarAction.WORD_MODE  -> R.string.extra_key_action_word_mode
        BarAction.SWITCH_BAR -> R.string.extra_key_action_switch
        BarAction.KEYBOARD   -> R.string.extra_key_action_keyboard
    }
)

/**
 * Key catalogue: grouped chips (a tap picks the key) plus a free-text section for
 * custom keys. Chips are ordinary focusables, so a D-pad walks them like anything else.
 * [initial] pre-fills the text section when editing an existing text key.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyPickerDialog(
    initial: ExtraKeyDef?,
    onPick: (ExtraKeyDef) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val touchless = remember { isTouchless(ctx) }
    val initialText = initial as? ExtraKeyDef.Text
    var text by remember { mutableStateOf(initialText?.text ?: "") }
    var label by remember { mutableStateOf(initialText?.label ?: "") }

    val navigation = listOf(SpecialKey.LEFT, SpecialKey.UP, SpecialKey.DOWN, SpecialKey.RIGHT,
        SpecialKey.HOME, SpecialKey.END, SpecialKey.PGUP, SpecialKey.PGDN)
    val editing = listOf(SpecialKey.ESC, SpecialKey.TAB, SpecialKey.ENTER, SpecialKey.BKSP, SpecialKey.DEL, SpecialKey.INS)
    val fKeys = SpecialKey.entries.filter { it.ordinal >= SpecialKey.F1.ordinal }

    AlertDialog(
        onDismissRequest = onDismiss,
        // The dialog window must not fit the IME itself, or its content can't scroll
        // above the keyboard; imePadding() on the column does the job instead.
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text(stringResource(R.string.extra_key_picker_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).imePadding()) {
                Group(stringResource(R.string.extra_key_group_navigation)) {
                    navigation.forEach { k -> Chip(k.label) { onPick(ExtraKeyDef.Special(k)) } }
                }
                Group(stringResource(R.string.extra_key_group_editing)) {
                    editing.forEach { k -> Chip(k.label) { onPick(ExtraKeyDef.Special(k)) } }
                }
                Group(stringResource(R.string.extra_key_group_modifiers)) {
                    ModKey.entries.forEach { m -> Chip(m.label) { onPick(ExtraKeyDef.Modifier(m)) } }
                }
                Group(stringResource(R.string.extra_key_group_function)) {
                    fKeys.forEach { k -> Chip(k.label) { onPick(ExtraKeyDef.Special(k)) } }
                }
                Group(stringResource(R.string.extra_key_group_actions)) {
                    BarAction.entries.forEach { a -> Chip(barActionName(a)) { onPick(ExtraKeyDef.Action(a)) } }
                }

                // ── Custom text ─────────────────────────────────────────────
                Text(
                    stringResource(R.string.extra_key_group_custom),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                if (touchless) {
                    TvTapField(value = text, onValueChange = { text = it },
                        label = stringResource(R.string.extra_key_text), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    TvTapField(value = label, onValueChange = { label = it },
                        label = stringResource(R.string.extra_key_label), modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true,
                        label = { Text(stringResource(R.string.extra_key_text)) }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = label, onValueChange = { label = it }, singleLine = true,
                        label = { Text(stringResource(R.string.extra_key_label)) }, modifier = Modifier.fillMaxWidth())
                }
                Text(
                    stringResource(R.string.extra_key_text_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Button(
                    onClick = { onPick(ExtraKeyDef.Text(text, label.trim().ifEmpty { null })) },
                    enabled = text.isNotEmpty(),
                    modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
                ) { Text(stringResource(R.string.extra_key_use_text)) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Group(title: String, chips: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy((-4).dp),
    ) { chips() }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label) })
}
