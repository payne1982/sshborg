package com.sshborg.ui.common

import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A single-choice setting. On touch it is the usual [ExposedDropdownMenuBox]; on a touchless
 * device (TV/D-pad) it becomes a [TvSelectField], whose plain anchor a D-pad can move past —
 * unlike the dropdown box, whose text-field anchor traps the focus on a TV.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SettingSelect(
    label: String,
    selected: T,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    touchless: Boolean,
    width: Dp = 180.dp,
) {
    val valueText = options.find { it.first == selected }?.second ?: options.firstOrNull()?.second ?: ""
    if (touchless) {
        TvSelectField(
            label = label,
            valueText = valueText,
            modifier = Modifier.width(width),
            showLabel = false,
        ) { dismiss ->
            options.forEach { (value, optLabel) ->
                DropdownMenuItem(text = { Text(optLabel) }, onClick = { onSelect(value); dismiss() })
            }
        }
    } else {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = valueText,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .width(width),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                singleLine = true,
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, optLabel) ->
                    DropdownMenuItem(text = { Text(optLabel) }, onClick = { onSelect(value); expanded = false })
                }
            }
        }
    }
}
