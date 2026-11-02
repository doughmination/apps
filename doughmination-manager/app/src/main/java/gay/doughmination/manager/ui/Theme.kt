package gay.doughmination.manager.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The Doughmination palette, as the System app and the websites use it.
val Accent = Color(0xFFEB459E)
val Ok = Color(0xFF7FD98B)
val Warn = Color(0xFFF2C46D)
val Bad = Color(0xFFFF8A9A)
val Muted = Color(0xFFA49EAB)

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A1430),
    onPrimaryContainer = Color(0xFFFFD8EC),
    secondary = Color(0xFFCDB8D6),
    background = Color(0xFF0F0D14),
    onBackground = Color(0xFFECE9EF),
    surface = Color(0xFF0F0D14),
    onSurface = Color(0xFFECE9EF),
    surfaceVariant = Color(0xFF1E1B2E),
    onSurfaceVariant = Muted,
    surfaceContainer = Color(0xFF17141F),
    surfaceContainerHigh = Color(0xFF1E1B2E),
    outline = Color(0xFF3B3546),
    outlineVariant = Color(0xFF2A2533),
    error = Bad,
)

@Composable
fun ManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
