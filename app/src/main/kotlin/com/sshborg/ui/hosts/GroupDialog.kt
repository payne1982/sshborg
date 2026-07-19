package com.sshborg.ui.hosts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sshborg.R
import com.sshborg.data.db.GroupEntity

/** Name + color-swatch dialog, used both to create and to edit a host group. */
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
                GroupEntity.SWATCHES.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { swatch ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { color = swatch }
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
