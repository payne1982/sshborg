package com.sshborg.ui.terminal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sshborg.R
import com.sshborg.data.BarAction
import com.sshborg.data.ExtraBar
import com.sshborg.data.ExtraBarPresets
import com.sshborg.data.ExtraKeyDef
import com.sshborg.data.ModKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val ExtraKeyFont = FontFamily(
    Font(R.font.roboto_condensed_regular),
    Font(R.font.roboto_condensed_bold, FontWeight.Bold),
)
private val ArrowKeyFont = FontFamily(
    Font(R.font.jetbrains_mono_regular),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/** Toggle states the bar reflects and the callbacks it drives; owned by the terminal screen. */
class ExtraBarState(
    val ctrlActive: Boolean,
    val altActive: Boolean,
    val wordMode: Boolean,
    val pinned: Boolean,
    val keyboardVisible: Boolean,
    val onCtrlToggle: () -> Unit,
    val onAltToggle: () -> Unit,
    val onWordModeToggle: () -> Unit,
    val onPinToggle: () -> Unit,
    val onKeyboardToggle: () -> Unit,
    /** Raw bytes to the session; the screen applies the sticky Ctrl/Alt to single bytes. */
    val onKey: (ByteArray) -> Unit,
    val cursorKeys: (Char) -> ByteArray,
)

/** Display name of a bar: custom bars carry their own, presets are localised. */
@Composable
fun extraBarName(bar: ExtraBar): String =
    if (bar.isPreset) stringResource(ExtraBarPresets.nameRes(bar.id)) else bar.name

/**
 * The extra-key bar, rendered from an [ExtraBar] description (issue #12). Rows with
 * `fit` share the width between their keys; other rows keep natural widths and
 * scroll horizontally. [bars] feeds the on-bar switch menu (custom first, then presets).
 */
@Composable
fun ExtraKeyBar(
    bar: ExtraBar,
    state: ExtraBarState,
    bars: List<ExtraBar>,
    onSelectBar: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontSize = bar.fontScale.sp.sp
    // The switch key swaps the keys for a row of bar names in place: same height, no
    // popup window, so the soft keyboard stays where it is and the terminal doesn't resize.
    var choosing by remember { mutableStateOf(false) }
    BackHandler(choosing) { choosing = false }

    Box(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (choosing) Modifier.alpha(0f).focusProperties { canFocus = false } else Modifier)
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            bar.rows.forEach { row ->
                val rowModifier =
                    if (row.fit) Modifier.fillMaxWidth()
                    else Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                Row(rowModifier, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    row.keys.forEach { key ->
                        val keyModifier = if (row.fit) Modifier.weight(1f) else Modifier
                        ExtraKeyItem(key, state, fontSize, keyModifier, fit = row.fit, onSwitch = { choosing = true })
                    }
                }
            }
        }
        if (choosing) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(Unit) { detectTapGestures { } }   // swallow taps between chips
                    .padding(horizontal = 2.dp, vertical = 2.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconKey(Icons.Filled.Close, stringResource(android.R.string.cancel), Modifier) { choosing = false }
                bars.forEach { b ->
                    ExtraKey(
                        label = extraBarName(b), fontSize = fontSize, active = b.id == bar.id,
                        onClick = { onSelectBar(b.id); choosing = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.ExtraKeyItem(
    key: ExtraKeyDef,
    state: ExtraBarState,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier,
    fit: Boolean,
    onSwitch: () -> Unit,
) {
    // Stretched keys get their room from the weight; a narrow phone with 9 columns
    // can't afford 8dp of padding each side around "Home" or "PgUp".
    val hPad = if (fit) 2.dp else 8.dp
    when (key) {
        is ExtraKeyDef.Special -> ExtraKey(
            label = key.displayLabel, fontSize = fontSize, modifier = modifier, hPad = hPad,
            isArrow = key.key.isArrow, repeatOnHold = key.repeatOnHold,
            onClick = { state.onKey(key.key.bytes(state.cursorKeys)) },
        )
        is ExtraKeyDef.Modifier -> ExtraKey(
            label = key.displayLabel, fontSize = fontSize, modifier = modifier, hPad = hPad,
            active = if (key.mod == ModKey.CTRL) state.ctrlActive else state.altActive,
            onClick = if (key.mod == ModKey.CTRL) state.onCtrlToggle else state.onAltToggle,
        )
        is ExtraKeyDef.Text -> ExtraKey(
            label = key.displayLabel, fontSize = fontSize, modifier = modifier, hPad = hPad,
            onClick = { state.onKey(key.unescaped.toByteArray(Charsets.UTF_8)) },
        )
        is ExtraKeyDef.Action -> when (key.action) {
            BarAction.PASTE -> {
                val clipboard = LocalClipboard.current
                val scope = rememberCoroutineScope()
                IconKey(Icons.Filled.ContentPaste, stringResource(R.string.terminal_paste_cd), modifier, iconSize = 24.dp, hPad = hPad) {
                    scope.launch {
                        clipboard.getClipEntry()?.clipData?.getItemAt(0)?.text?.toString()
                            ?.toByteArray(Charsets.UTF_8)
                            ?.let { state.onKey(it) }
                    }
                }
            }
            BarAction.PIN -> IconKey(
                if (state.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                stringResource(R.string.terminal_pin_keys_cd), modifier, active = state.pinned, hPad = hPad,
                onClick = state.onPinToggle,
            )
            BarAction.WORD_MODE -> IconKey(
                Icons.Filled.Spellcheck, null, modifier, active = state.wordMode, hPad = hPad,
                onClick = state.onWordModeToggle,
            )
            BarAction.KEYBOARD -> IconKey(
                Icons.Filled.Keyboard, stringResource(R.string.terminal_keyboard_cd), modifier, hPad = hPad,
                active = state.keyboardVisible, onClick = state.onKeyboardToggle,
            )
            BarAction.SWITCH_BAR -> IconKey(
                Icons.Filled.SwapHoriz, stringResource(R.string.terminal_switch_bar_cd), modifier, hPad = hPad,
                onClick = onSwitch,
            )
        }
    }
}

@Composable
private fun IconKey(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier,
    active: Boolean = false,
    iconSize: Dp = 18.dp,
    hPad: Dp = 8.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .background(
                if (active) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
                MaterialTheme.shapes.extraSmall,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = hPad, vertical = if (iconSize >= 24.dp) 4.dp else 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                   else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ExtraKey(
    label: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    hPad: Dp = 8.dp,
    active: Boolean = false,
    isArrow: Boolean = false,
    // Hold-to-repeat, like the keyboard's own backspace (issue #11): tap = one press,
    // hold = keep firing until released. Off by default so ordinary keys still tap once.
    repeatOnHold: Boolean = false,
    onClick: () -> Unit,
) {
    val bg        = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val textColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val interaction = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    // For repeat keys we drive the gesture ourselves (fire on down, then auto-repeat),
    // so clickable is replaced by pointerInput + an explicit ripple to keep the press
    // feedback. Timings follow the system key-repeat values, matching backspace.
    val pressModifier = if (repeatOnHold) {
        Modifier
            .indication(interaction, ripple())
            .pointerInput(onClick) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val press = PressInteraction.Press(down.position)
                    interaction.tryEmit(press)
                    onClick()  // immediate first press, like backspace on key-down
                    val repeatJob = scope.launch {
                        delay(android.view.ViewConfiguration.getKeyRepeatTimeout().toLong())
                        while (isActive) {
                            onClick()
                            delay(android.view.ViewConfiguration.getKeyRepeatDelay().toLong())
                        }
                    }
                    val up = waitForUpOrCancellation()
                    repeatJob.cancel()
                    interaction.tryEmit(
                        if (up != null) PressInteraction.Release(press) else PressInteraction.Cancel(press)
                    )
                }
            }
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Box(
        modifier = modifier
            .background(bg, MaterialTheme.shapes.extraSmall)
            .then(pressModifier)
            .padding(horizontal = if (isArrow && hPad >= 8.dp) 10.dp else hPad, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = fontSize,
            fontFamily = if (isArrow) ArrowKeyFont else ExtraKeyFont,
            fontWeight = if (active || isArrow) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            color = textColor,
        )
    }
}
