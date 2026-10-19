package com.tdater.blackbox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Restrained workbench palette: near-black surfaces, a single amber accent.
private val Amber = Color(0xFFFFB547)
private val AmberDim = Color(0xFF5C3F00)

private val Dark = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2A1B00),
    primaryContainer = AmberDim,
    onPrimaryContainer = Color(0xFFFFDDB0),
    secondary = Color(0xFF9EC9D8),
    onSecondary = Color(0xFF00343F),
    secondaryContainer = Color(0xFF1E3A44),
    onSecondaryContainer = Color(0xFFCDEBF6),
    background = Color(0xFF0E0F11),
    onBackground = Color(0xFFE4E2DE),
    surface = Color(0xFF141518),
    onSurface = Color(0xFFE4E2DE),
    surfaceVariant = Color(0xFF24262B),
    onSurfaceVariant = Color(0xFFC3C6CC),
    surfaceContainer = Color(0xFF1A1C20),
    surfaceContainerHigh = Color(0xFF212328),
    outline = Color(0xFF6F7379),
    outlineVariant = Color(0xFF3A3D43),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF4A0000),
    errorContainer = Color(0xFF5C1512),
    onErrorContainer = Color(0xFFFFDAD5),
)

private val Light = lightColorScheme(
    primary = Color(0xFF7A5300),
    primaryContainer = Color(0xFFFFDDB0),
    secondary = Color(0xFF2D6170),
)

@Composable
fun BlackboxTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
