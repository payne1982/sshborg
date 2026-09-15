package com.sshborg.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val FocusBorderWidth = 3.dp

/**
 * An [OutlinedButton] whose focus can be seen from across a room.
 *
 * Material's outlined button keeps its thin grey outline and grey label whether it is focused
 * or not — the focused colour tokens equal the resting ones — so the only cue is a 10% state
 * layer, about 1.2:1 against the button at rest: invisible on a TV driven by a D-pad. Here a
 * focused button also gets a thick outline and label in onSurface, which stands clear of the
 * grey resting style in both themes.
 *
 * Touch is unaffected: tapping a button does not give it focus, so the emphasis only ever
 * appears while navigating with a remote or a keyboard.
 */
@Composable
fun FocusOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val emphasis = focused && enabled
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        border = if (emphasis) BorderStroke(FocusBorderWidth, MaterialTheme.colorScheme.onSurface)
                 else ButtonDefaults.outlinedButtonBorder(enabled),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (emphasis) MaterialTheme.colorScheme.onSurface else Color.Unspecified,
        ),
        interactionSource = interaction,
        content = content,
    )
}
