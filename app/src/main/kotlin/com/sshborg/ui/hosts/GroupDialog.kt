package com.sshborg.ui.hosts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sshborg.R
import com.sshborg.data.db.GroupEntity

/** Quick-pick swatches plus an RGB slider picker; any ARGB color can be produced. */
@Composable
fun ColorPickerContent(color: Int, onColorChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GroupEntity.SWATCHES.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { swatch ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { onColorChange(swatch) }
                            .then(
                                if (swatch == color)
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier
                            )
                            .padding(4.dp)
                            .background(Color(swatch), CircleShape),
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .padding(3.dp)
                    .background(Color(color), CircleShape)
            )
            Text(
                String.format("#%06X", color and 0xFFFFFF),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        RgbSlider("R", r) { onColorChange(0xFF000000.toInt() or (it shl 16) or (g shl 8) or b) }
        RgbSlider("G", g) { onColorChange(0xFF000000.toInt() or (r shl 16) or (it shl 8) or b) }
        RgbSlider("B", b) { onColorChange(0xFF000000.toInt() or (r shl 16) or (g shl 8) or it) }
    }
}

@Composable
private fun RgbSlider(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
        )
        Text(
            value.toString().padStart(3),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Name + color-picker dialog, used both to create and to edit a host group. */
@Composable
fun GroupDialog(
    title: String,
    initialName: String,
    initialColor: Int,
    onConfirm: (name: String, color: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name  by remember { mutableStateOf(initialName) }
    var color by remember { mutableIntStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.group_dialog_name)) },
                    singleLine = true,
                )
                ColorPickerContent(color = color, onColorChange = { color = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim(), color) },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Color-picker dialog for a host's own color, which overrides the group color. */
@Composable
fun HostColorDialog(
    initialColor: Int?,
    onConfirm: (color: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var color by remember { mutableIntStateOf(initialColor ?: GroupEntity.SWATCHES[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.host_color_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ColorPickerContent(color = color, onColorChange = { color = it })
                if (initialColor != null) {
                    TextButton(onClick = { onConfirm(null) }) {
                        Text(stringResource(R.string.host_color_remove))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(color) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
