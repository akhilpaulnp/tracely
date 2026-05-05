package dev.tracely.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4F8CFF),
    onPrimary = Color.White,
    secondary = Color(0xFF4FDC7C),
    tertiary = Color(0xFFFFB84F),
    background = Color(0xFF0F1117),
    surface = Color(0xFF1A1D27),
    surfaceVariant = Color(0xFF252833),
    onBackground = Color(0xFFE4E6EB),
    onSurface = Color(0xFFE4E6EB),
    onSurfaceVariant = Color(0xFF8B8F9E),
    outline = Color(0xFF2D3040),
    error = Color(0xFFFF4F6A),
)

@Composable
fun TracelyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content,
    )
}
