package co.omnimusic.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C6FF),
    onPrimary = Color(0xFF1F2A5C),
    primaryContainer = Color(0xFF35407A),
    secondary = Color(0xFF8FD8C6),
    surface = Color(0xFF121318),
    surfaceVariant = Color(0xFF1C1E25),
    onSurface = Color(0xFFE6E6EC),
    onSurfaceVariant = Color(0xFFB9BAC6),
    background = Color(0xFF0C0D11),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3B4AA0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFF1F6F5C),
    surface = Color(0xFFFBFBFF),
    surfaceVariant = Color(0xFFEDEEF6),
    onSurface = Color(0xFF191A20),
    onSurfaceVariant = Color(0xFF57586A),
)

@Composable
fun OmniMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

/**
 * A deterministic accent per catalog entity, so rows and artwork placeholders are visually stable
 * without needing to download anything.
 */
fun accentFor(seed: String): Color {
    val hue = (seed.hashCode() and 0x7FFFFFFF) % 360
    return Color.hsl(hue.toFloat(), 0.55f, 0.55f)
}
