package com.tdater.blackbox.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Looks the user can pick in Settings. Flight recorder is the original; the rest are calmer for everyday use. */
enum class ThemeChoice(val label: String, val blurb: String) {
    SYSTEM("Match my phone", "Flight recorder at night, Daylight in the day"),
    FLIGHT("Flight recorder", "Carbon, international orange and instrument cyan"),
    DAYLIGHT("Daylight", "Warm paper, easy to read outside"),
    MIDNIGHT("Midnight", "Deep blue, soft and quiet"),
    WALLPAPER("My wallpaper", "Colours from your wallpaper (Android 12+)"),
}

/** Accent and status colours plus the backdrop. [techy] turns on the grid, hazard tape and monospace labels. */
@Immutable
data class Palette(
    val orange: Color,
    val orangeHot: Color,
    val cyan: Color,
    val violet: Color,
    val good: Color,
    val bad: Color,
    val warn: Color,
    val bgTop: Color,
    val bgMid: Color,
    val bgBottom: Color,
    val grid: Color,
    val glowA: Color,
    val glowB: Color,
    val dark: Boolean,
    val techy: Boolean,
)

private val FlightPalette = Palette(
    orange = Color(0xFFFF6B1A), orangeHot = Color(0xFFFF9A3D), cyan = Color(0xFF3FE0D0), violet = Color(0xFF8B7CFF),
    good = Color(0xFF4BE38A), bad = Color(0xFFFF5A5F), warn = Color(0xFFFFC23D),
    bgTop = Color(0xFF0B0C12), bgMid = Color(0xFF0E0D16), bgBottom = Color(0xFF08090D),
    grid = Color.White.copy(alpha = 0.025f), glowA = Color(0xFFFF6B1A).copy(alpha = 0.16f), glowB = Color(0xFF3FE0D0).copy(alpha = 0.08f),
    dark = true, techy = true,
)

private val DaylightPalette = Palette(
    orange = Color(0xFFC2410C), orangeHot = Color(0xFFEA580C), cyan = Color(0xFF0F766E), violet = Color(0xFF6D28D9),
    good = Color(0xFF15803D), bad = Color(0xFFB91C1C), warn = Color(0xFFB45309),
    bgTop = Color(0xFFFCF9F4), bgMid = Color(0xFFF7F2EA), bgBottom = Color(0xFFF2ECE3),
    grid = Color.Transparent, glowA = Color(0xFFFB923C).copy(alpha = 0.10f), glowB = Color(0xFF14B8A6).copy(alpha = 0.07f),
    dark = false, techy = false,
)

private val MidnightPalette = Palette(
    orange = Color(0xFF8AB4FF), orangeHot = Color(0xFFB4CEFF), cyan = Color(0xFF5EEAD4), violet = Color(0xFFC4B5FD),
    good = Color(0xFF4ADE80), bad = Color(0xFFF87171), warn = Color(0xFFFBBF24),
    bgTop = Color(0xFF0B1020), bgMid = Color(0xFF0E1530), bgBottom = Color(0xFF080C18),
    grid = Color.Transparent, glowA = Color(0xFF6D8CFF).copy(alpha = 0.16f), glowB = Color(0xFF2DD4BF).copy(alpha = 0.08f),
    dark = true, techy = false,
)

val LocalPalette = staticCompositionLocalOf { FlightPalette }

/** Theme-aware accent colours. Read them in composition; capture into a local before using inside draw lambdas. */
object BbColors {
    val Orange: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.orange
    val OrangeHot: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.orangeHot
    val Cyan: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.cyan
    val Violet: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.violet
    val Good: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.good
    val Bad: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.bad
    val Warn: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.warn
}

private fun darkScheme(p: Palette, surface: Color, panel: Color, high: Color, highest: Color, variant: Color) = darkColorScheme(
    primary = p.orange,
    onPrimary = Color(0xFF1A0A00),
    primaryContainer = p.orange.copy(alpha = 0.28f).compositeOver(surface),
    onPrimaryContainer = Color(0xFFFFE6D6),
    secondary = p.cyan,
    onSecondary = Color(0xFF00201D),
    secondaryContainer = p.cyan.copy(alpha = 0.22f).compositeOver(surface),
    onSecondaryContainer = Color(0xFFD6FFF9),
    tertiary = p.violet,
    onTertiary = Color(0xFF14104A),
    // Transparent so every screen floats over the shared backdrop drawn in MainActivity.
    background = Color.Transparent,
    onBackground = Color(0xFFECEAF2),
    surface = surface,
    onSurface = Color(0xFFECEAF2),
    surfaceVariant = variant,
    onSurfaceVariant = Color(0xFFB4B9C8),
    surfaceContainerLowest = p.bgBottom,
    surfaceContainerLow = surface,
    surfaceContainer = panel,
    surfaceContainerHigh = high,
    surfaceContainerHighest = highest,
    outline = Color(0xFF6B7186),
    outlineVariant = Color(0xFF2E3344),
    error = p.bad,
    onError = Color(0xFF2A0003),
    errorContainer = Color(0xFF5A1418),
    onErrorContainer = Color(0xFFFFDAD8),
)

private val FlightScheme = darkScheme(FlightPalette, Color(0xFF14161E), Color(0xFF171A23), Color(0xFF1E2230), Color(0xFF272B3A), Color(0xFF262A36))
private val MidnightScheme = darkScheme(MidnightPalette, Color(0xFF121A33), Color(0xFF151E3A), Color(0xFF1B2547), Color(0xFF223057), Color(0xFF233056))

private val DaylightScheme = lightColorScheme(
    primary = DaylightPalette.orange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3D3),
    onPrimaryContainer = Color(0xFF3B1300),
    secondary = DaylightPalette.cyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1F5EF),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = DaylightPalette.violet,
    background = Color.Transparent,
    onBackground = Color(0xFF1F1A17),
    surface = Color(0xFFFFFDFA),
    onSurface = Color(0xFF1F1A17),
    surfaceVariant = Color(0xFFF0E9E0),
    onSurfaceVariant = Color(0xFF5B534C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFBF6),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF7F1EA),
    surfaceContainerHighest = Color(0xFFF0E9E0),
    outline = Color(0xFF8A817A),
    outlineVariant = Color(0xFFE4DCD2),
    error = DaylightPalette.bad,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF450A0A),
)

private fun typography(techy: Boolean): Typography = Typography().let { t ->
    val labelFamily = if (techy) FontFamily.Monospace else FontFamily.Default
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.2).sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.Bold),
        labelLarge = TextStyle(fontFamily = labelFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = if (techy) 1.4.sp else 0.4.sp),
        labelMedium = TextStyle(fontFamily = labelFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = if (techy) 0.8.sp else 0.2.sp),
        labelSmall = TextStyle(fontFamily = labelFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = if (techy) 0.8.sp else 0.2.sp),
    )
}

private val BbShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Resolves a choice to the palette actually shown (SYSTEM and WALLPAPER depend on the phone). */
@Composable
fun resolvePalette(choice: ThemeChoice): Pair<Palette, ColorScheme> {
    val systemDark = isSystemInDarkTheme()
    return when (choice) {
        ThemeChoice.FLIGHT -> FlightPalette to FlightScheme
        ThemeChoice.DAYLIGHT -> DaylightPalette to DaylightScheme
        ThemeChoice.MIDNIGHT -> MidnightPalette to MidnightScheme
        ThemeChoice.SYSTEM -> if (systemDark) FlightPalette to FlightScheme else DaylightPalette to DaylightScheme
        ThemeChoice.WALLPAPER -> {
            if (Build.VERSION.SDK_INT < 31) return if (systemDark) MidnightPalette to MidnightScheme else DaylightPalette to DaylightScheme
            val ctx = LocalContext.current
            val dyn = if (systemDark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            val base = if (systemDark) MidnightPalette else DaylightPalette
            base.copy(
                orange = dyn.primary, orangeHot = dyn.primary, cyan = dyn.tertiary, violet = dyn.secondary,
                bgTop = dyn.surface, bgMid = dyn.surfaceContainerLow, bgBottom = dyn.surfaceContainerLowest,
                glowA = dyn.primary.copy(alpha = 0.12f), glowB = dyn.tertiary.copy(alpha = 0.08f),
            ) to dyn.copy(background = Color.Transparent)
        }
    }
}

@Composable
fun BlackboxTheme(choice: ThemeChoice = ThemeChoice.FLIGHT, content: @Composable () -> Unit) {
    val (palette, scheme) = resolvePalette(choice)
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = typography(palette.techy), shapes = BbShapes, content = content)
    }
}
