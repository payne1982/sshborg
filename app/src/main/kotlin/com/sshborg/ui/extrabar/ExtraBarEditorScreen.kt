package com.sshborg.ui.extrabar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sshborg.R
import com.sshborg.data.BarFontScale
import com.sshborg.data.ExtraBar
import com.sshborg.isTouchless
import com.sshborg.ui.common.SettingSelect
import com.sshborg.ui.common.TvTapField
import com.sshborg.ui.terminal.ExtraKeyBar
import com.sshborg.ui.terminal.previewExtraBarState

private enum class PickerMode { INSERT, REPLACE }

/**
 * WYSIWYG editor for a custom extra-key bar (issue #12). The real bar is rendered at the
 * top in editing mode: a tap (or OK on a D-pad) selects a key, and the toolbar below acts
 * on the selection. One interaction model for touch and remote — no drag & drop needed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtraBarEditorScreen(
    barId: String,
    onBack: () -> Unit,
    vm: ExtraBarEditorViewModel = viewModel(),
) {
    val ctx = LocalContext.current
    val touchless = remember { isTouchless(ctx) }
    val newName = stringResource(R.string.extra_bars_new)
    LaunchedEffect(barId) { vm.load(barId, newName) }

    val draft by vm.draft.collectAsState()
    val selected by vm.selected.collectAsState()
    val dirty by vm.dirty.collectAsState()
    var picker by remember { mutableStateOf<PickerMode?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val back = { if (dirty) confirmDiscard = true else onBack() }
    BackHandler(onBack = back)

    val bar = draft
    val canSave = bar != null && bar.name.isNotBlank() && bar.rows.any { it.keys.isNotEmpty() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(
                        if (barId == ExtraBarEditorViewModel.NEW_ID) R.string.extra_bar_editor_new_title
                        else R.string.extra_bar_editor_title
                    ))
                },
                navigationIcon = {
                    IconButton(onClick = back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { vm.save(onBack) }, enabled = canSave) {
                        Icon(Icons.Default.Check, stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        if (bar == null) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding(),
        ) {
            // ── Preview = the bar itself ────────────────────────────────────
            ExtraKeyBar(
                bar = bar,
                state = previewExtraBarState(),
                bars = emptyList(),
                onSelectBar = {},
                editing = true,
                selected = selected,
                onSelectKey = { vm.select(it) },
            )
            Text(
                stringResource(R.string.extra_bar_hint_select),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // ── Selection toolbar ───────────────────────────────────────────
            val sel = selected
            val hasSel = sel != null && bar.rows.getOrNull(sel.first)?.keys?.getOrNull(sel.second) != null
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, R.string.extra_bar_move_left,
                    enabled = hasSel && sel!!.second > 0) { vm.moveKey(-1) }
                ToolButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, R.string.extra_bar_move_right,
                    enabled = hasSel && sel!!.second < bar.rows[sel.first].keys.lastIndex) { vm.moveKey(1) }
                ToolButton(Icons.Default.KeyboardArrowUp, R.string.extra_bar_move_up,
                    enabled = hasSel && sel!!.first > 0) { vm.moveKeyToRow(-1) }
                ToolButton(Icons.Default.KeyboardArrowDown, R.string.extra_bar_move_down,
                    enabled = hasSel && sel!!.first < bar.rows.lastIndex) { vm.moveKeyToRow(1) }
                ToolButton(Icons.Default.Edit, R.string.extra_bar_edit_key, enabled = hasSel) { picker = PickerMode.REPLACE }
                ToolButton(Icons.Default.Add, R.string.extra_bar_add_key, enabled = true) { picker = PickerMode.INSERT }
                ToolButton(Icons.Default.Delete, R.string.extra_bar_remove_key, enabled = hasSel) { vm.removeKey() }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ── Rows ────────────────────────────────────────────────────────
            bar.rows.forEachIndexed { i, row ->
                ListItem(
                    headlineContent = { Text(stringResource(R.string.extra_bar_row_n, i + 1, row.keys.size)) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Two named alternatives beat a switch plus a sentence explaining it.
                            SingleChoiceSegmentedButtonRow {
                                SegmentedButton(
                                    selected = !row.fit, onClick = { vm.setRowFit(i, false) },
                                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                                ) { Text(stringResource(R.string.extra_bar_row_scroll)) }
                                SegmentedButton(
                                    selected = row.fit, onClick = { vm.setRowFit(i, true) },
                                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                                ) { Text(stringResource(R.string.extra_bar_row_fill)) }
                            }
                            if (bar.rows.size > 1) {
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = { vm.removeRow(i) }) {
                                    Icon(Icons.Default.Delete, stringResource(R.string.extra_bar_remove_row_cd))
                                }
                            }
                        }
                    },
                )
            }
            if (bar.rows.size < ExtraBar.MAX_ROWS) {
                TextButton(onClick = { vm.addRow() }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.extra_bar_add_row))
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ── Bar options ─────────────────────────────────────────────────
            if (touchless) {
                TvTapField(
                    value = bar.name,
                    onValueChange = { vm.setName(it) },
                    label = stringResource(R.string.extra_bar_name),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            } else {
                OutlinedTextField(
                    value = bar.name,
                    onValueChange = { vm.setName(it) },
                    label = { Text(stringResource(R.string.extra_bar_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            val fontOptions = listOf(
                BarFontScale.SMALL  to stringResource(R.string.extra_bar_font_small),
                BarFontScale.MEDIUM to stringResource(R.string.extra_bar_font_medium),
                BarFontScale.LARGE  to stringResource(R.string.extra_bar_font_large),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.extra_bar_font_size)) },
                trailingContent = {
                    SettingSelect(
                        label = stringResource(R.string.extra_bar_font_size),
                        selected = bar.fontScale,
                        options = fontOptions,
                        onSelect = { vm.setFontScale(it) },
                        touchless = touchless,
                        width = 150.dp,
                    )
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    picker?.let { mode ->
        val current = if (mode == PickerMode.REPLACE)
            selected?.let { (r, i) -> bar?.rows?.getOrNull(r)?.keys?.getOrNull(i) } else null
        KeyPickerDialog(
            initial = current,
            onPick = { key ->
                if (mode == PickerMode.REPLACE) vm.replaceKey(key) else vm.insertKey(key)
                picker = null
            },
            onDismiss = { picker = null },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.extra_bar_discard_title)) },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onBack() }) {
                    Text(stringResource(R.string.action_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun ToolButton(icon: ImageVector, cdRes: Int, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = stringResource(cdRes))
    }
}
