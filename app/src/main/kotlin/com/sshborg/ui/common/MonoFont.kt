package com.sshborg.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * The terminal's own font, for everywhere else that needs columns to line up.
 *
 * [FontFamily.Monospace] is whatever the device calls monospace, and what it hands back for a
 * character it does not have is not monospaced at all — which is how a hex dump ends up with
 * ragged columns. This is the font the app already ships for the terminal, so it costs nothing
 * to use and it looks the same on every phone.
 */
@Composable
fun monoFont(): FontFamily {
    val assets = LocalContext.current.assets
    return remember(assets) {
        FontFamily(
            Font("fonts/JetBrainsMonoNerdFontMono-Regular.ttf", assets),
            Font("fonts/JetBrainsMonoNerdFontMono-Bold.ttf", assets, FontWeight.Bold),
        )
    }
}
