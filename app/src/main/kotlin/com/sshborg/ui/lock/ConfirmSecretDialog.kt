package com.sshborg.ui.lock

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.sshborg.R
import kotlinx.coroutines.launch

/**
 * Asks for the current PIN/passphrase and calls [onVerified] once [verify] accepts it.
 * Used before changing the lock away from the in-app secret (to another mode or off),
 * mirroring how the OS requires the current secret to change a screen lock.
 */
@Composable
fun ConfirmSecretDialog(
    onDismiss: () -> Unit,
    verify: suspend (CharArray) -> Boolean,
    onVerified: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var reveal by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lock_confirm_current_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it; wrong = false },
                label = { Text(stringResource(R.string.lock_current)) },
                singleLine = true,
                isError = wrong,
                visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { reveal = !reveal }) {
                        Icon(
                            if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            stringResource(R.string.lock_reveal_cd),
                        )
                    }
                },
                supportingText = if (wrong) ({ Text(stringResource(R.string.lock_incorrect)) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = value.isNotEmpty(),
                onClick = { scope.launch { if (verify(value.toCharArray())) onVerified() else wrong = true } },
            ) { Text(stringResource(R.string.lock_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
