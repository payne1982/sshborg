package com.sshborg.ui.lock

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.sshborg.R
import com.sshborg.data.AppLockManager
import com.sshborg.isTouchless
import com.sshborg.ui.common.TvTapField
import kotlinx.coroutines.launch

private const val PIN_MIN = 4
private const val PIN_MAX = 12
private const val PASSPHRASE_MIN = 4

/**
 * Dialog to set or change the in-app lock secret. The user picks a kind (PIN or
 * passphrase), enters it twice, and [onConfirm] receives the chosen kind and the
 * secret. When [verifyCurrent] is non-null (changing an existing secret), the current
 * secret must be entered and verified first, so a change can't quietly replace it with
 * something the user picked by mistake.
 */
@Composable
fun LockSecretDialog(
    onDismiss: () -> Unit,
    onConfirm: (AppLockManager.Kind, CharArray) -> Unit,
    verifyCurrent: (suspend (CharArray) -> Boolean)? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val touchless = remember { isTouchless(context) }
    var current by remember { mutableStateOf("") }
    var currentWrong by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf(AppLockManager.Kind.PIN) }
    var secret by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }

    // Switching kind clears the new fields so a PIN can't leak into passphrase rules.
    fun selectKind(k: AppLockManager.Kind) { if (k != kind) { kind = k; secret = ""; confirm = "" } }

    val isPin = kind == AppLockManager.Kind.PIN
    val longEnough = if (isPin) secret.length in PIN_MIN..PIN_MAX && secret.all { it.isDigit() }
                     else secret.length >= PASSPHRASE_MIN
    val matches = secret == confirm
    val needsCurrent = verifyCurrent != null
    val canSave = longEnough && matches && (!needsCurrent || current.isNotEmpty())

    val error = when {
        currentWrong                       -> stringResource(R.string.lock_incorrect)
        secret.isNotEmpty() && !longEnough -> stringResource(R.string.lock_too_short)
        confirm.isNotEmpty() && !matches   -> stringResource(R.string.lock_mismatch)
        else -> null
    }

    val keyboard = KeyboardOptions(
        keyboardType = if (isPin) KeyboardType.NumberPassword else KeyboardType.Password,
    )
    val filterInput: (String) -> String =
        { if (isPin) it.filter(Char::isDigit).take(PIN_MAX) else it }

    val revealVisual = if (reveal) VisualTransformation.None else PasswordVisualTransformation()
    val revealIcon: @Composable () -> Unit = {
        IconButton(onClick = { reveal = !reveal }) {
            Icon(
                if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                stringResource(R.string.lock_reveal_cd),
            )
        }
    }

    val onSave = {
        if (needsCurrent) {
            scope.launch {
                if (verifyCurrent!!(current.toCharArray())) onConfirm(kind, secret.toCharArray())
                else currentWrong = true
            }
            Unit
        } else onConfirm(kind, secret.toCharArray())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lock_set_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (needsCurrent) {
                    if (touchless) {
                        TvTapField(
                            value = current,
                            onValueChange = { current = it; currentWrong = false },
                            label = stringResource(R.string.lock_current),
                            modifier = Modifier.fillMaxWidth(),
                            keyboardType = KeyboardType.Password,
                            isPassword = true,
                        )
                    } else {
                        OutlinedTextField(
                            value = current,
                            onValueChange = { current = it; currentWrong = false },
                            label = { Text(stringResource(R.string.lock_current)) },
                            singleLine = true,
                            isError = currentWrong,
                            visualTransformation = revealVisual,
                            trailingIcon = revealIcon,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    HorizontalDivider()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    KindOption(stringResource(R.string.lock_kind_pin), isPin) { selectKind(AppLockManager.Kind.PIN) }
                    KindOption(stringResource(R.string.lock_kind_passphrase), !isPin) { selectKind(AppLockManager.Kind.PASSPHRASE) }
                }
                val secretKeyboardType = if (isPin) KeyboardType.NumberPassword else KeyboardType.Password
                if (touchless) {
                    TvTapField(
                        value = secret,
                        onValueChange = { secret = filterInput(it) },
                        label = stringResource(if (isPin) R.string.lock_enter_pin else R.string.lock_enter_passphrase),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardType = secretKeyboardType,
                        isPassword = true,
                    )
                } else {
                    OutlinedTextField(
                        value = secret,
                        onValueChange = { secret = filterInput(it) },
                        label = { Text(stringResource(if (isPin) R.string.lock_enter_pin else R.string.lock_enter_passphrase)) },
                        singleLine = true,
                        visualTransformation = revealVisual,
                        trailingIcon = revealIcon,
                        keyboardOptions = keyboard,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (touchless) {
                    TvTapField(
                        value = confirm,
                        onValueChange = { confirm = filterInput(it) },
                        label = stringResource(R.string.lock_confirm),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardType = secretKeyboardType,
                        isPassword = true,
                    )
                } else {
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = filterInput(it) },
                        label = { Text(stringResource(R.string.lock_confirm)) },
                        singleLine = true,
                        isError = confirm.isNotEmpty() && !matches,
                        visualTransformation = revealVisual,
                        trailingIcon = revealIcon,
                        keyboardOptions = keyboard,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = onSave) {
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
