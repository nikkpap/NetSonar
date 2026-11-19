package gr.nikkpap.netsonar.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Accent colours: bright neon on dark, deeper tones on light (readable on white). */
object Accent {
    var isDark = true
    val cyan get() = if (isDark) Color(0xFF22D3EE) else Color(0xFF0E7490)
    val violet get() = if (isDark) Color(0xFFA78BFA) else Color(0xFF6D28D9)
    val amber get() = if (isDark) Color(0xFFF59E0B) else Color(0xFFB45309)
    val green get() = if (isDark) Color(0xFF34D399) else Color(0xFF047857)
    val pink get() = if (isDark) Color(0xFFF472B6) else Color(0xFFBE185D)
    val blue get() = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D4ED8)
    val red get() = if (isDark) Color(0xFFFB7185) else Color(0xFFBE123C)
    /** Text/icon colour on top of a filled accent background. */
    val ink get() = if (isDark) Color(0xFF06101F) else Color.White
}

val LocalDark = staticCompositionLocalOf { true }

private val DarkColors = darkColorScheme(
    primary = Color(0xFF22D3EE), onPrimary = Color(0xFF06101F),
    secondary = Color(0xFFA78BFA), onSecondary = Color(0xFF1E1240),
    tertiary = Color(0xFF34D399), onTertiary = Color(0xFF06101F),
    background = Color(0xFF060A13), onBackground = Color(0xFFE6EDF7),
    surface = Color(0xFF0D1424), onSurface = Color(0xFFE6EDF7),
    surfaceVariant = Color(0xFF16203A), onSurfaceVariant = Color(0xFF94A3C0),
    surfaceContainer = Color(0xFF0B1222),
    outline = Color(0xFF26324F), error = Color(0xFFFB7185),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E7490), onPrimary = Color.White,
    secondary = Color(0xFF6D28D9), onSecondary = Color.White,
    tertiary = Color(0xFF059669), onTertiary = Color.White,
    background = Color(0xFFF2F6FC), onBackground = Color(0xFF0B1324),
    surface = Color.White, onSurface = Color(0xFF0B1324),
    surfaceVariant = Color(0xFFE6ECF5), onSurfaceVariant = Color(0xFF55657F),
    surfaceContainer = Color.White,
    outline = Color(0xFFD5DEEB), error = Color(0xFFE11D48),
)

@Composable
fun NetSonarTheme(dark: Boolean, content: @Composable () -> Unit) {
    Accent.isDark = dark
    val scheme = if (dark) DarkColors else LightColors
    CompositionLocalProvider(LocalDark provides dark) {
        MaterialTheme(colorScheme = scheme, typography = Typography()) {
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
        }
    }
}
