package app.mcleaner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    val Bg = Color(0xFF121417)
    val Surface = Color(0xFF1B1E23)
    val SurfaceHigh = Color(0xFF252A31)
    val Accent = Color(0xFF5DA9F0)
    val Good = Color(0xFF5DCAA5)
    val Warn = Color(0xFFEF9F27)
    val Bad = Color(0xFFE24B4A)
    val TextMuted = Color(0xFF9AA3AD)
}

private val colors = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Color(0xFF04223D),
    secondary = Palette.Good,
    background = Palette.Bg,
    surface = Palette.Surface,
    surfaceVariant = Palette.SurfaceHigh,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextMuted,
    error = Palette.Bad,
)

private val typography = Typography(
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun MCleanerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
