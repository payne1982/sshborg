package com.sshborg.ui.theme

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82B1FF),
    onPrimary = Color(0xFF003060),
    secondary = Color(0xFF80CBC4),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0),
    // Snackbar uses inverseSurface/inverseOnSurface — force light-on-dark contrast
    inverseSurface = Color(0xFFE0E0E0),
    inverseOnSurface = Color(0xFF1E1E1E),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF00897B),
    background = Color(0xFFECEFF1),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1C1C1C),
    onSurface = Color(0xFF1C1C1C),
    // Snackbar uses inverseSurface/inverseOnSurface — force dark-on-light contrast
    inverseSurface = Color(0xFF1C1C1C),
    inverseOnSurface = Color(0xFFECEFF1),
)

@Composable
fun SshBorgTheme(
    nightMode: Int = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (nightMode) {
        AppCompatDelegate.MODE_NIGHT_YES -> true
        AppCompatDelegate.MODE_NIGHT_NO  -> false
        else                             -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
