package com.sshborg.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sshborg.R

/**
 * Shared presentation for any blocking error or disconnect: an optional icon and title, a short
 * human summary line, side-by-side primary + "Copy error" actions (kept above the detail so they
 * are always on-screen), and a collapsed, scrollable, selectable technical detail below with a
 * copy button. Used by the terminal (disconnect + connection error) and the SFTP error screen so
 * every blocking error looks and behaves the same.
 *
 * @param detail full technical cause; when null/blank the details section and Copy button are hidden.
 */
@Composable
fun ProblemContent(
    title: String,
    summary: String?,
    detail: String?,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val strCopied = stringResource(R.string.action_copied)
    var expanded by remember { mutableStateOf(false) }

    // The terminal drives the soft keyboard through its own AndroidView, not a Compose text
    // field, so Compose's keyboard controller can't dismiss it. Hide it via InputMethodManager
    // on the window token (same as the terminal does) when the detail is expanded — otherwise
    // the keyboard sits over it. Not hidden on mere appearance: the terminal re-shows it on
    // resume anyway, so forcing it down there just causes a pointless up/down flicker.
    val hideKeyboard = {
        val imm = ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    Column(
        modifier = modifier.widthIn(max = 340.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(8.dp))
        }

        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)

        if (summary != null) {
            Spacer(Modifier.height(8.dp))
            Text(summary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }

        // Actions first, side by side, so they are always on-screen — the expandable detail below
        // can grow tall and must never push Copy/Close off the bottom. Copy works while collapsed.
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPrimary) { Text(primaryLabel) }
            if (!detail.isNullOrBlank()) {
                OutlinedButton(onClick = {
                    val cb = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                    cb.setPrimaryClip(android.content.ClipData.newPlainText("SSHBorg error", detail))
                    android.widget.Toast.makeText(ctx, strCopied, android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(R.string.terminal_copy_error))
                }
            }
        }

        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { expanded = !expanded; if (expanded) hideKeyboard() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.terminal_error_details), style = MaterialTheme.typography.labelLarge)
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    SelectionContainer {
                        Text(
                            detail,
                            modifier = Modifier
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}
