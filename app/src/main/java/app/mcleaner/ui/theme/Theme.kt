package app.mcleaner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.mcleaner.data.ThemeMode

/** One set of colours per theme; accents are darker in the light theme to stay readable on white. */
data class PaletteColors(
    val bg: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val accent: Color,
    val onAccent: Color,
    val good: Color,
    val warn: Color,
    val bad: Color,
    val text: Color,
    val textMuted: Color,
)

private val Dark = PaletteColors(
    bg = Color(0xFF121417),
    surface = Color(0xFF1B1E23),
    surfaceHigh = Color(0xFF252A31),
    accent = Color(0xFF5DA9F0),
    onAccent = Color(0xFF04223D),
    good = Color(0xFF5DCAA5),
    warn = Color(0xFFEF9F27),
    bad = Color(0xFFE24B4A),
    text = Color(0xFFE8EAED),
    textMuted = Color(0xFF9AA3AD),
)

private val Light = PaletteColors(
    bg = Color(0xFFF2F4F7),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFE3E8EE),
    accent = Color(0xFF1565C0),
    onAccent = Color(0xFFFFFFFF),
    good = Color(0xFF1B7F5E),
    warn = Color(0xFFA35C00),
    bad = Color(0xFFC62828),
    text = Color(0xFF1B1E23),
    textMuted = Color(0xFF5B6672),
)

private val LocalPalette = staticCompositionLocalOf { Dark }

/** Theme-aware accent colours for places the Material scheme does not cover (badges, warnings, bars). */
object Palette {
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accent
    val Good: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.good
    val Warn: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.warn
    val Bad: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.bad
}

private fun PaletteColors.scheme(dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = accent, onPrimary = onAccent, secondary = good, background = bg, surface = surface,
        surfaceVariant = surfaceHigh, surfaceContainer = surface, surfaceContainerHigh = surfaceHigh,
        onSurfaceVariant = textMuted, error = bad, onBackground = text, onSurface = text,
    )
} else {
    lightColorScheme(
        primary = accent, onPrimary = onAccent, secondary = good, background = bg, surface = surface,
        surfaceVariant = surfaceHigh, surfaceContainer = surface, surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = surfaceHigh, surfaceContainerLow = surface,
        onSurfaceVariant = textMuted, error = bad, onBackground = text, onSurface = text,
    )
}

private val typography = Typography(
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Medium),
)

/** «Как в системе» follows the head unit's day/night mode. */
@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun MCleanerTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val p = if (dark) Dark else Light
    MaterialTheme(colorScheme = p.scheme(dark), typography = typography) {
        // Screens draw on plain backgrounds (no Surface), so set the default text colour explicitly:
        // otherwise Text without a colour falls back to black.
        CompositionLocalProvider(LocalPalette provides p, LocalContentColor provides p.text, content = content)
    }
}

/** Window background for the bars and the first frame. */
fun windowColor(dark: Boolean): Int = (if (dark) Dark.bg else Light.bg).let {
    android.graphics.Color.argb((it.alpha * 255).toInt(), (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt())
}
