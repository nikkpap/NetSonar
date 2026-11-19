package gr.nikkpap.netsonar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

object Accent {
    val cyan = Color(0xFF22D3EE)
    val violet = Color(0xFFA78BFA)
    val amber = Color(0xFFF59E0B)
    val green = Color(0xFF34D399)
    val pink = Color(0xFFF472B6)
    val blue = Color(0xFF60A5FA)
    val red = Color(0xFFFB7185)
    val ink = Color(0xFF06101F)
}

val LocalDark = staticCompositionLocalOf { true }

private val DarkColors = darkColorScheme(
    primary = Accent.cyan, onPrimary = Accent.ink,
    secondary = Accent.violet, onSecondary = Color(0xFF1E1240),
    tertiary = Accent.green, onTertiary = Accent.ink,
    background = Color(0xFF060A13), onBackground = Color(0xFFE6EDF7),
    surface = Color(0xFF0D1424), onSurface = Color(0xFFE6EDF7),
    surfaceVariant = Color(0xFF16203A), onSurfaceVariant = Color(0xFF94A3C0),
    surfaceContainer = Color(0xFF0B1222),
    outline = Color(0xFF26324F), error = Accent.red,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0891B2), onPrimary = Color.White,
    secondary = Color(0xFF7C3AED), onSecondary = Color.White,
    tertiary = Color(0xFF059669), onTertiary = Color.White,
    background = Color(0xFFF2F6FC), onBackground = Color(0xFF0B1324),
    surface = Color.White, onSurface = Color(0xFF0B1324),
    surfaceVariant = Color(0xFFE6ECF5), onSurfaceVariant = Color(0xFF55657F),
    surfaceContainer = Color.White,
    outline = Color(0xFFD5DEEB), error = Color(0xFFE11D48),
)

@Composable
fun NetSonarTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDark provides dark) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = Typography(), content = content)
    }
}
