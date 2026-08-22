package com.sshborg.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Opens a context menu when the remote/keyboard "Menu" (options) key is pressed while this
 * element is focused. Lets a D-pad reach a row's actions without needing a focusable overflow
 * button (trailing buttons inside a clickable row aren't reachable) or a long-press. CENTER is
 * left to the row's primary action. No-op when [enabled] is false, and harmless on touch since
 * no Menu key is ever sent there. A long-press still opens the menu on remotes with a held OK.
 */
fun Modifier.onMenuKey(enabled: Boolean = true, onMenu: () -> Unit): Modifier =
    if (!enabled) this
    else onPreviewKeyEvent { ev ->
        if (ev.type == KeyEventType.KeyDown && ev.key == Key.Menu) { onMenu(); true } else false
    }
