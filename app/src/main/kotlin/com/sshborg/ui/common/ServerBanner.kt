package com.sshborg.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sshborg.R
import com.sshborg.isTouchless
import kotlinx.coroutines.launch

/**
 * What a server says before it lets anyone in, above the field the password goes into.
 *
 * A banner is a legal notice as often as it is a greeting, and either way it is worth nothing
 * after the fact: it has to be readable *before* anything is typed. So it comes with the prompt
 * and not after it — which is also why the prompt itself is raised during the connection, see
 * [com.sshborg.data.ssh.SshAuth.Interactive].
 *
 * Shown as it arrived, in the terminal's own font because banners are drawn with spaces, in a
 * box that stops at [maxHeight] and scrolls: a server with sixty lines of policy must not push
 * the password field off the dialog. It scrolls sideways as well and never wraps — a banner is
 * laid out for eighty columns, and a dialog is narrower than that: wrapped, its frame breaks in
 * half and a centred title lands on two lines. Selectable, because a banner sometimes carries a
 * reference number somebody has to write down.
 */
@Composable
fun ServerBanner(
    text: String,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 150.dp,
) {
    val scroll = rememberScrollState()
    val sideways = rememberScrollState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val touchless = remember(context) { isTouchless(context) }
    // A remote has no finger to drag with, so the box takes focus and the D-pad scrolls it. The
    // keys are passed on once there is nothing left that way, or focus could never leave it —
    // and while it is here the box is outlined, because focus nobody can see is issue #3 again.
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // And it is handed the focus as the dialog opens, which is the only way it gets any: a
    // D-pad walk never finds it from the field below (measured on the TV emulator — UP from the
    // password field does nothing at all), and the field, once focused, raises the television's
    // own keyboard, which then swallows every key of the remote. Asking first also puts the
    // banner where it belongs on a television: read before anything is typed, not behind a
    // keyboard. From here DOWN passes through to the field once there is nothing left to scroll.
    val requester = remember { FocusRequester() }
    LaunchedEffect(touchless) { if (touchless) runCatching { requester.requestFocus() } }
    val step = with(LocalDensity.current) { maxHeight.toPx() / 3f }
    val dpad = Modifier
        .focusRequester(requester)
        .focusable(enabled = touchless, interactionSource = interaction)
        .onPreviewKeyEvent { ev ->
            if (!touchless || ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val down = ev.key == Key.DirectionDown
            val up = ev.key == Key.DirectionUp
            val right = ev.key == Key.DirectionRight
            val left = ev.key == Key.DirectionLeft
            val state = if (right || left) sideways else scroll
            val forward = down || right
            val room = (forward && state.canScrollForward) ||
                ((up || left) && state.canScrollBackward)
            if (!room) return@onPreviewKeyEvent false
            scope.launch { state.animateScrollBy(if (forward) step else -step) }
            true
        }

    Column(modifier) {
        Text(
            stringResource(R.string.password_server_banner),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(4.dp),
            border = if (focused) BorderStroke(FOCUS_BORDER, MaterialTheme.colorScheme.onSurface) else null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SelectionContainer {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = monoFont(),
                    softWrap = false,
                    modifier = dpad
                        .heightIn(max = maxHeight)
                        .verticalScroll(scroll)
                        .horizontalScroll(sideways)
                        .padding(8.dp),
                )
            }
        }
    }
}

private val FOCUS_BORDER = 3.dp
