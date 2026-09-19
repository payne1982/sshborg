package com.sshborg.ui.sftp

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sshborg.R
import com.sshborg.service.FileFailure
import com.sshborg.ui.common.FocusOutlinedButton

/**
 * What went wrong in one SFTP operation: a single file, a batch, or a background transfer.
 * [notAttempted] names the files a lost connection never reached.
 */
data class ErrorReport(
    val title: String,
    val failures: List<FileFailure>,
    val notAttempted: List<String> = emptyList(),
) {
    /** The whole report as plain text, stack traces included — what Copy puts on the clipboard. */
    fun asText(context: Context): String = buildString {
        appendLine(title)
        failures.forEach { f -> appendLine("✗ " + listOf(f.name, f.message).filter { it.isNotEmpty() }.joinToString(" — ")) }
        if (notAttempted.isNotEmpty()) {
            appendLine(context.getString(R.string.sftp_report_not_attempted, notAttempted.joinToString(", ")))
        }
        failures.filter { it.detail.isNotBlank() }.forEach { f ->
            appendLine()
            if (f.name.isNotEmpty()) appendLine("── ${f.name} ──")
            append(f.detail.trimEnd()).appendLine()
        }
    }.trimEnd()
}

/**
 * Shows an [ErrorReport] until the user closes it — unlike a snackbar, it can be read, and copied,
 * at leisure. Each failed file is a row with its reason; tapping a row unfolds its stack trace.
 * The list scrolls inside a body capped at half the screen, so a batch with many failures never
 * pushes the buttons off it. Rows are focusable, so a D-pad walks down the list and scrolls it.
 */
@Composable
fun ErrorReportDialog(report: ErrorReport, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val strCopied = stringResource(R.string.action_copied)
    val maxBody = (LocalConfiguration.current.screenHeightDp * 0.5f).dp

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(report.title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = maxBody)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                report.failures.forEach { FailureRow(it) }
                if (report.notAttempted.isNotEmpty()) {
                    Text(
                        stringResource(R.string.sftp_report_not_attempted, report.notAttempted.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
        dismissButton = {
            FocusOutlinedButton(onClick = {
                val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cb.setPrimaryClip(android.content.ClipData.newPlainText("SSHBorg error", report.asText(ctx)))
                android.widget.Toast.makeText(ctx, strCopied, android.widget.Toast.LENGTH_SHORT).show()
            }) {
                Text(stringResource(R.string.terminal_copy_error))
            }
        },
    )
}

@Composable
private fun FailureRow(failure: FileFailure) {
    var open by remember { mutableStateOf(false) }
    val hasDetail = failure.detail.isNotBlank()
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .clickable { if (hasDetail) open = !open }
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (failure.name.isNotEmpty()) {
                    Text(
                        failure.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    failure.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (hasDetail) {
                Icon(
                    if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(R.string.terminal_error_details),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (open) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp),
            ) {
                SelectionContainer {
                    Text(
                        failure.detail.trimEnd(),
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}
