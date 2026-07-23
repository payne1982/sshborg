package com.sshborg.ui.hosts

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sshborg.R
import com.sshborg.data.db.GroupEntity

/**
 * Classic gradient color picker (saturation/brightness square + hue bar) with
 * quick-pick swatches and a hex preview; any RGB color can be produced.
 *
 * Hue/saturation/brightness live in local state instead of being re-derived
 * from [color]: grays and blacks map to many HSV triples, and re-deriving
 * would snap the hue thumb to 0 while dragging through them.
 */
@Composable
fun ColorPickerContent(color: Int, onColorChange: (Int) -> Unit) {
    val initialHsv = remember { FloatArray(3).also { AndroidColor.colorToHSV(color, it) } }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var sat by remember { mutableFloatStateOf(initialHsv[1]) }
    var bri by remember { mutableFloatStateOf(initialHsv[2]) }

    fun setHsv(h: Float, s: Float, b: Float) {
        hue = h; sat = s; bri = b
        onColorChange(AndroidColor.HSVToColor(floatArrayOf(h, s, b)))
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GroupEntity.SWATCHES.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { swatch ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clickable {
                                val hsv = FloatArray(3).also { AndroidColor.colorToHSV(swatch, it) }
                                setHsv(hsv[0], hsv[1], hsv[2])
                            }
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

        // Saturation (x) / brightness (y) square for the current hue
        var svSize by remember { mutableStateOf(IntSize.Zero) }
        fun svUpdate(pos: Offset) {
            if (svSize == IntSize.Zero) return
            setHsv(
                hue,
                (pos.x / svSize.width).coerceIn(0f, 1f),
                1f - (pos.y / svSize.height).coerceIn(0f, 1f),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                .onSizeChanged { svSize = it }
                .pointerInput(Unit) { detectTapGestures { svUpdate(it) } }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ -> change.consume(); svUpdate(change.position) }
                },
        ) {
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (sat * svSize.width).toInt() - 10.dp.roundToPx(),
                            ((1f - bri) * svSize.height).toInt() - 10.dp.roundToPx(),
                        )
                    }
                    .size(20.dp)
                    .border(1.dp, Color(0x80000000.toInt()), CircleShape)
                    .padding(1.dp)
                    .border(2.dp, Color.White, CircleShape),
            )
        }

        // Hue bar
        var hueSize by remember { mutableStateOf(IntSize.Zero) }
        fun hueUpdate(pos: Offset) {
            if (hueSize == IntSize.Zero) return
            setHsv((pos.x / hueSize.width).coerceIn(0f, 1f) * 360f, sat, bri)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.horizontalGradient(
                        (0..6).map { Color.hsv(it * 60f, 1f, 1f) }
                    )
                )
                .onSizeChanged { hueSize = it }
                .pointerInput(Unit) { detectTapGestures { hueUpdate(it) } }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ -> change.consume(); hueUpdate(change.position) }
                },
        ) {
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (hue / 360f * hueSize.width).toInt() - 10.dp.roundToPx(),
                            (hueSize.height - 20.dp.roundToPx()) / 2,
                        )
                    }
                    .size(20.dp)
                    .border(1.dp, Color(0x80000000.toInt()), CircleShape)
                    .padding(1.dp)
                    .border(2.dp, Color.White, CircleShape),
            )
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
