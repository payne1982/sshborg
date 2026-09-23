package com.sshborg.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A dialog body that scrolls, with a chevron at whichever edge has more content behind it.
 *
 * A dialog sizes itself to its content, so a list that does not fit simply stops at the bottom
 * with no edge, no shadow and nothing moving: there is no way to tell a list that ends from a
 * list that continues. The chevrons say which it is, and they double as the cue for a D-pad,
 * where scrolling happens by moving focus and the list can be several screens long.
 *
 * [maxHeight] defaults to half the screen, which leaves the title and the buttons on it.
 */
@Composable
fun ScrollingDialogBody(
    modifier: Modifier = Modifier,
    maxHeight: Dp = (LocalConfiguration.current.screenHeightDp * 0.5f).dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    Box(modifier) {
        Column(
            Modifier
                .heightIn(max = maxHeight)
                .verticalScroll(scroll),
            content = content,
        )
        if (scroll.canScrollBackward) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
        if (scroll.canScrollForward) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
