package com.sshborg.ui.extrabar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sshborg.R
import com.sshborg.data.ExtraBar
import com.sshborg.data.ExtraBarPresets
import com.sshborg.ui.common.onMenuKey
import com.sshborg.ui.terminal.extraBarName

/**
 * Extra-key bar picker and manager (issue #12): custom bars first, then the presets.
 * Tap a row to make it the active bar; the row menu edits/duplicates/deletes.
 * Duplicating a preset is the intended way to start a custom bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtraBarsScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    vm: ExtraBarsViewModel = viewModel(),
) {
    val custom by vm.customBars.collectAsState()
    val selectedId by vm.selectedId.collectAsState()
    var toDelete by remember { mutableStateOf<ExtraBar?>(null) }
    val copyFmt = stringResource(R.string.extra_bars_copy_name)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.extra_bars_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEdit(ExtraBarEditorViewModel.NEW_ID) }) {
                Icon(Icons.Default.Add, stringResource(R.string.extra_bars_new))
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (custom.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.extra_bars_section_custom)) }
                items(custom, key = { it.id }) { bar ->
                    BarRow(
                        bar = bar, selected = bar.id == selectedId,
                        onSelect = { vm.select(bar.id) },
                        onEdit = { onEdit(bar.id) },
                        onDuplicate = { vm.duplicate(bar, copyFmt.format(bar.name)) { onEdit(it) } },
                        onDelete = { toDelete = bar },
                    )
                }
            }
            item { SectionHeader(stringResource(R.string.extra_bars_section_presets)) }
            items(ExtraBarPresets.all, key = { it.id }) { bar ->
                val name = extraBarName(bar)
                BarRow(
                    bar = bar, selected = bar.id == selectedId,
                    onSelect = { vm.select(bar.id) },
                    onEdit = null,
                    onDuplicate = { vm.duplicate(bar, copyFmt.format(name)) { onEdit(it) } },
                    onDelete = null,
                )
            }
            item { Box(Modifier.padding(bottom = 80.dp)) }
        }
    }

    toDelete?.let { bar ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.extra_bars_delete_title, bar.name)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(bar.id); toDelete = null }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun BarRow(
    bar: ExtraBar,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: (() -> Unit)?,
    onDuplicate: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(extraBarName(bar)) },
        supportingContent = {
            Text(
                bar.rows.joinToString("  /  ") { row ->
                    row.keys.joinToString(" ") { it.displayLabel.ifEmpty { "•" } }
                },
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        leadingContent = { RadioButton(selected = selected, onClick = onSelect) },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.hosts_options_cd))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (onEdit != null) DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_edit)) },
                        onClick = { menu = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_duplicate)) },
                        onClick = { menu = false; onDuplicate() },
                    )
                    if (onDelete != null) DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        },
        // Menu key (TV remote) opens the row menu; CENTER selects. The trailing ⋮ serves touch.
        modifier = Modifier
            .clickable(onClick = onSelect)
            .onMenuKey { menu = true },
    )
    HorizontalDivider(thickness = 0.5.dp)
}
