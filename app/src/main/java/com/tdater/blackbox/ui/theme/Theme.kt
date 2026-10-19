package com.tdater.blackbox.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// "Flight recorder" palette: real black boxes are international orange, so that is the accent,
// backed by an instrument-cyan secondary on deep carbon. Status colours live in BbColors.
object BbColors {
    val Orange = Color(0xFFFF6B1A)
    val OrangeHot = Color(0xFFFF9A3D)
    val Cyan = Color(0xFF3FE0D0)
    val Violet = Color(0xFF8B7CFF)
    val Good = Color(0xFF4BE38A)
    val Bad = Color(0xFFFF5A5F)
    val Warn = Color(0xFFFFC23D)
    val Carbon = Color(0xFF0A0B10)
    val CarbonHigh = Color(0xFF14161E)
    val Panel = Color(0xFF171A23)
}

private val Dark = darkColorScheme(
    primary = BbColors.Orange,
    onPrimary = Color(0xFF1A0A00),
    primaryContainer = Color(0xFF4A1F05),
    onPrimaryContainer = Color(0xFFFFD9C2),
    secondary = BbColors.Cyan,
    onSecondary = Color(0xFF00201D),
    secondaryContainer = Color(0xFF0E3A37),
    onSecondaryContainer = Color(0xFFB8FFF6),
    tertiary = BbColors.Violet,
    onTertiary = Color(0xFF14104A),
    // Transparent so every screen floats over the shared backdrop drawn in MainActivity.
    background = Color.Transparent,
    onBackground = Color(0xFFECEAF2),
    surface = BbColors.CarbonHigh,
    onSurface = Color(0xFFECEAF2),
    surfaceVariant = Color(0xFF262A36),
    onSurfaceVariant = Color(0xFFB4B9C8),
    surfaceContainerLowest = BbColors.Carbon,
    surfaceContainerLow = Color(0xFF12141B),
    surfaceContainer = BbColors.Panel,
    surfaceContainerHigh = Color(0xFF1E2230),
    surfaceContainerHighest = Color(0xFF272B3A),
    outline = Color(0xFF6B7186),
    outlineVariant = Color(0xFF2E3344),
    error = BbColors.Bad,
    onError = Color(0xFF2A0003),
    errorContainer = Color(0xFF5A1418),
    onErrorContainer = Color(0xFFFFDAD8),
)

private val Light = lightColorScheme(
    primary = Color(0xFFB54100),
    primaryContainer = Color(0xFFFFDBC9),
    secondary = Color(0xFF006A62),
)

private val Mono = FontFamily.Monospace

private val BbType = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.2).sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.Bold),
        labelLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.4.sp),
        labelMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.8.sp),
        labelSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.8.sp),
    )
}

private val BbShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun BlackboxTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = BbType, shapes = BbShapes, content = content)
}
