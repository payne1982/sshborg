package com.sshborg.ui.lock

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sshborg.R
import com.sshborg.data.AppLockManager

private const val PIN_MIN = 4
private const val PIN_MAX = 8
private const val PASSPHRASE_MIN = 4

/**
 * Dialog to set or change the in-app lock secret. The user picks a kind (PIN or
 * passphrase), enters it twice, and [onConfirm] receives the chosen kind and the
 * secret. Validation enforces a numeric 4–8 digit PIN or a passphrase of at least
 * four characters, and that both entries match.
 */
@Composable
fun LockSecretDialog(
    onDismiss: () -> Unit,
    onConfirm: (AppLockManager.Kind, CharArray) -> Unit,
) {
    var kind by remember { mutableStateOf(AppLockManager.Kind.PIN) }
    var secret by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    // Switching kind clears the fields so a PIN can't leak into passphrase rules.
    fun selectKind(k: AppLockManager.Kind) { if (k != kind) { kind = k; secret = ""; confirm = "" } }

    val isPin = kind == AppLockManager.Kind.PIN
    val longEnough = if (isPin) secret.length in PIN_MIN..PIN_MAX && secret.all { it.isDigit() }
                     else secret.length >= PASSPHRASE_MIN
    val matches = secret == confirm
    val valid = longEnough && matches

    val error = when {
        secret.isNotEmpty() && !longEnough -> stringResource(R.string.lock_too_short)
        confirm.isNotEmpty() && !matches   -> stringResource(R.string.lock_mismatch)
        else -> null
    }

    val keyboard = KeyboardOptions(
        keyboardType = if (isPin) KeyboardType.NumberPassword else KeyboardType.Password,
    )
    val filterInput: (String) -> String =
        { if (isPin) it.filter(Char::isDigit).take(PIN_MAX) else it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lock_set_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    KindOption(stringResource(R.string.lock_kind_pin), isPin) { selectKind(AppLockManager.Kind.PIN) }
                    KindOption(stringResource(R.string.lock_kind_passphrase), !isPin) { selectKind(AppLockManager.Kind.PASSPHRASE) }
                }
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = filterInput(it) },
                    label = { Text(stringResource(if (isPin) R.string.lock_enter_pin else R.string.lock_enter_passphrase)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = keyboard,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = filterInput(it) },
                    label = { Text(stringResource(R.string.lock_confirm)) },
                    singleLine = true,
                    isError = confirm.isNotEmpty() && !matches,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = keyboard,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(kind, secret.toCharArray()) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun KindOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.selectable(selected = selected, onClick = onSelect),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label)
    }
}
