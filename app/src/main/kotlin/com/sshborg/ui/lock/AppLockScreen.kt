package com.sshborg.ui.lock

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import com.sshborg.R
import com.sshborg.data.AppLockManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PIN_MIN = 4
private const val PIN_MAX = 12

/**
 * Full-screen lock gate. Renders a numeric keypad ([AppLockManager.Kind.PIN]) or a masked
 * passphrase field, verifies via [verify], and calls [onUnlocked] on success. Handles the
 * escalating lockout returned by the manager with a live countdown. Usable by touch, by a
 * hardware keyboard, and — for the PIN — by a TV remote's number keys and D-pad.
 */
@Composable
fun AppLockScreen(
    kind: AppLockManager.Kind,
    verify: suspend (CharArray) -> AppLockManager.VerifyResult,
    onUnlocked: () -> Unit,
    initialLockoutSeconds: Long = 0,
) {
    val scope = rememberCoroutineScope()
    val incorrectText = stringResource(R.string.lock_incorrect)
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var lockoutUntil by remember {
        mutableStateOf(if (initialLockoutSeconds > 0) System.currentTimeMillis() + initialLockoutSeconds * 1000 else 0L)
    }
    var remaining by remember { mutableStateOf(initialLockoutSeconds) }

    // The remaining lockout is read from disk, so on a gate that went up in the same frame as
    // the app it arrives a moment later, when this composable is already running.
    LaunchedEffect(initialLockoutSeconds) {
        if (initialLockoutSeconds > 0) {
            lockoutUntil = System.currentTimeMillis() + initialLockoutSeconds * 1000
        }
    }

    // Live countdown while locked out.
    LaunchedEffect(lockoutUntil) {
        while (lockoutUntil > System.currentTimeMillis()) {
            remaining = (lockoutUntil - System.currentTimeMillis() + 999) / 1000
            delay(500)
        }
        remaining = 0
    }
    val enabled = remaining <= 0

    val submit: () -> Unit = submit@{
        if (!enabled || input.isEmpty()) return@submit
        val chars = input.toCharArray()
        input = ""
        scope.launch {
            when (val r = verify(chars)) {
                is AppLockManager.VerifyResult.Success -> onUnlocked()
                is AppLockManager.VerifyResult.Wrong -> error = incorrectText
                is AppLockManager.VerifyResult.LockedOut -> {
                    error = null
                    lockoutUntil = System.currentTimeMillis() + r.secondsRemaining * 1000
                }
            }
            chars.fill('\u0000')
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp)) {
            // A TV is landscape and short: a single centred column overflows and clips the
            // bottom keypad row, so there we place the header/dots beside the keypad instead.
            val landscape = maxWidth > maxHeight

            val message = when {
                remaining > 0 -> stringResource(R.string.lock_locked_out, remaining)
                error != null -> error
                else -> null
            }
            val header: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    val prompt = if (kind == AppLockManager.Kind.PIN)
                        stringResource(R.string.lock_enter_pin) else stringResource(R.string.lock_enter_passphrase)
                    Text(prompt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        message ?: " ",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.heightIn(min = 24.dp),
                    )
                }
            }

            if (kind == AppLockManager.Kind.PIN) {
                PinKeypad(
                    landscape = landscape,
                    header = header,
                    value = input,
                    enabled = enabled,
                    onChange = { input = it; error = null },
                    onSubmit = submit,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    header()
                    Spacer(Modifier.height(16.dp))
                    PassphraseInput(
                        value = input,
                        enabled = enabled,
                        onChange = { input = it; error = null },
                        onSubmit = submit,
                    )
                }
            }
        }
    }
}

@Composable
private fun PinKeypad(
    landscape: Boolean,
    header: @Composable () -> Unit,
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    val append = { d: Char -> if (enabled && value.length < PIN_MAX) onChange(value + d) }
    val backspace = { if (enabled && value.isNotEmpty()) onChange(value.dropLast(1)) }

    // Dots grow with the entered digits (the max length is not revealed); the eye
    // reveals the digits, since a forgotten PIN can't be recovered.
    var reveal by remember { mutableStateOf(false) }
    val dots = @Composable {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.heightIn(min = 28.dp), contentAlignment = Alignment.Center) {
                if (reveal) {
                    Text(value, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(value.length) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        }
                    }
                }
            }
            IconButton(onClick = { reveal = !reveal }) {
                Icon(
                    if (reveal) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    stringResource(R.string.lock_reveal_cd),
                )
            }
        }
    }

    // The digit grid holds the focus (so the remote's number keys are captured here) and,
    // via onPreviewKeyEvent, a hardware/remote numeric keypad and Enter.
    val grid = @Composable {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.onPreviewKeyEvent { ev ->
                if (!enabled || ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.Zero, Key.NumPad0 -> { append('0'); true }
                    Key.One, Key.NumPad1 -> { append('1'); true }
                    Key.Two, Key.NumPad2 -> { append('2'); true }
                    Key.Three, Key.NumPad3 -> { append('3'); true }
                    Key.Four, Key.NumPad4 -> { append('4'); true }
                    Key.Five, Key.NumPad5 -> { append('5'); true }
                    Key.Six, Key.NumPad6 -> { append('6'); true }
                    Key.Seven, Key.NumPad7 -> { append('7'); true }
                    Key.Eight, Key.NumPad8 -> { append('8'); true }
                    Key.Nine, Key.NumPad9 -> { append('9'); true }
                    Key.Backspace, Key.Delete -> { backspace(); true }
                    Key.Enter, Key.NumPadEnter -> { if (value.length >= PIN_MIN) onSubmit(); true }
                    else -> false
                }
            },
        ) {
            val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'))
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { d ->
                        KeyButton(
                            modifier = if (d == '1') Modifier.focusRequester(focusRequester) else Modifier,
                            enabled = enabled,
                            onClick = { append(d) },
                        ) { Text(d.toString(), style = MaterialTheme.typography.headlineSmall) }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                KeyButton(enabled = enabled && value.isNotEmpty(), onClick = backspace) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, stringResource(R.string.lock_delete_cd))
                }
                KeyButton(enabled = enabled, onClick = { append('0') }) {
                    Text("0", style = MaterialTheme.typography.headlineSmall)
                }
                KeyButton(
                    enabled = enabled && value.length >= PIN_MIN,
                    onClick = onSubmit,
                    container = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                ) { Icon(Icons.Default.LockOpen, stringResource(R.string.lock_submit_cd)) }
            }
        }
    }

    if (landscape) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                header()
                Spacer(Modifier.height(16.dp))
                dots()
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { grid() }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            header()
            Spacer(Modifier.height(20.dp))
            dots()
            Spacer(Modifier.height(24.dp))
            grid()
        }
    }
}

private val FocusRingWidth = 3.dp
private val FocusRingGap = 3.dp

@Composable
private fun KeyButton(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: Color = MaterialTheme.colorScheme.onSurface,
    inner: @Composable () -> Unit,
) {
    // Material's only focus cue is a 10% state layer: on the dark theme a grey key turns a
    // slightly lighter grey (1.27:1), which can't be seen from a sofa with a remote. A focused
    // key also gets a ring, drawn just outside it with a gap, in onSurface. Set against the
    // background rather than the key, it reads the same on the grey digits and on the
    // primary-coloured unlock key, in both themes. It is drawn outside the key's bounds so
    // the keypad measures exactly as before: in landscape it only just fits a TV screen.
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ring = MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = container,
        contentColor = content,
        interactionSource = interaction,
        modifier = modifier
            .size(68.dp)
            .drawBehind {
                if (focused) {
                    val stroke = FocusRingWidth.toPx()
                    drawCircle(
                        color = ring,
                        radius = size.minDimension / 2 + FocusRingGap.toPx() + stroke / 2,
                        style = Stroke(stroke),
                    )
                }
            },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { inner() }
    }
}

@Composable
private fun PassphraseInput(
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    var reveal by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            trailingIcon = {
                IconButton(onClick = { reveal = !reveal }) {
                    Icon(
                        if (reveal) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        stringResource(R.string.lock_reveal_cd),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSubmit, enabled = enabled && value.isNotEmpty()) {
            Text(stringResource(R.string.lock_submit_cd))
        }
    }
}
