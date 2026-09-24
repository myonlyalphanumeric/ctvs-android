package it.citiverse.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import it.citiverse.app.data.ThemeMode

/** Colori fissi dello stile del feed (gli stessi del CSS di riferimento). */
val VoteUp = Color(0xFFFF4500)
val VoteDown = Color(0xFF6A5CFF)
val PinGreen = Color(0xFF1F9D55)
val Brand = Color(0xFFD9230F)

@Immutable
data class CvColors(
    val bg: Color,
    val card: Color,
    val border: Color,
    val pill: Color,
    val text: Color,
    val muted: Color,
    val fedi: Color,
    val fediBg: Color,
    val isDark: Boolean,
)

private val LightCv = CvColors(
    bg = Color(0xFFF4F5F7),
    card = Color(0xFFFFFFFF),
    border = Color(0xFFE2E5E9),
    pill = Color(0xFFEEF0F3),
    text = Color(0xFF1B1D21),
    muted = Color(0xFF6A717C),
    fedi = Color(0xFF6D4BD8),
    fediBg = Color(0xFF6D4BD8).copy(alpha = 0.12f),
    isDark = false,
)

private val DarkCv = CvColors(
    bg = Color(0xFF0F1115),
    card = Color(0xFF171A1F),
    border = Color(0xFF2A2F37),
    pill = Color(0xFF242930),
    text = Color(0xFFE7E9EC),
    muted = Color(0xFF9AA3AE),
    fedi = Color(0xFFB9A6FF),
    fediBg = Color(0xFF6D4BD8).copy(alpha = 0.22f),
    isDark = true,
)

val LocalCv = staticCompositionLocalOf { LightCv }

object Cv {
    val colors: CvColors
        @Composable get() = LocalCv.current
}

private val LightScheme = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD4),
    onPrimaryContainer = Color(0xFF410001),
    secondary = Color(0xFF6D4BD8),
    background = LightCv.bg,
    onBackground = LightCv.text,
    surface = LightCv.card,
    onSurface = LightCv.text,
    surfaceVariant = LightCv.pill,
    onSurfaceVariant = LightCv.muted,
    surfaceContainer = LightCv.card,
    surfaceContainerLow = LightCv.bg,
    surfaceContainerHigh = LightCv.pill,
    outline = Color(0xFFC6CAD0),
    outlineVariant = LightCv.border,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFF7A66),
    onPrimary = Color(0xFF3B0600),
    primaryContainer = Color(0xFF5C1107),
    onPrimaryContainer = Color(0xFFFFDAD4),
    secondary = Color(0xFFB9A6FF),
    background = DarkCv.bg,
    onBackground = DarkCv.text,
    surface = DarkCv.card,
    onSurface = DarkCv.text,
    surfaceVariant = DarkCv.pill,
    onSurfaceVariant = DarkCv.muted,
    surfaceContainer = DarkCv.card,
    surfaceContainerLow = DarkCv.bg,
    surfaceContainerHigh = DarkCv.pill,
    outline = Color(0xFF4A515B),
    outlineVariant = DarkCv.border,
)

private val AppTypography = Typography().let { t ->
    t.copy(
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
        bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
    )
}

@Composable
fun CitiverseTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalCv provides if (dark) DarkCv else LightCv) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            content = content,
        )
    }
}

/** "#DC9656" → Color, con ripiego se il valore non è valido. */
fun parseHexColor(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    val h = hex.trim().removePrefix("#")
    val full = when (h.length) {
        3 -> h.map { "$it$it" }.joinToString("")
        6 -> h
        8 -> h.substring(0, 6)
        else -> return fallback
    }
    return full.toLongOrNull(16)?.let { Color(0xFF000000 or it) } ?: fallback
}
