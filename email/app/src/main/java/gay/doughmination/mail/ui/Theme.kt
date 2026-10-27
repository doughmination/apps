package gay.doughmination.mail.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The site's palette (email/src/web/app.css).
val Accent = Color(0xFFEB459E)
val Danger = Color(0xFFFF5C7A)
val Muted = Color(0xFFA49EAB)

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A1A2C),
    onPrimaryContainer = Color(0xFFFFD8EA),
    secondaryContainer = Color(0xFF2A1A24),
    onSecondaryContainer = Color(0xFFFFD8EA),
    background = Color(0xFF0B0A0D),
    onBackground = Color(0xFFECE9EF),
    surface = Color(0xFF0B0A0D),
    onSurface = Color(0xFFECE9EF),
    surfaceVariant = Color(0xFF1C1A20),
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Color(0xFF0B0A0D),
    surfaceContainerLow = Color(0xFF131116),
    surfaceContainer = Color(0xFF17151B),
    surfaceContainerHigh = Color(0xFF1E1B22),
    surfaceContainerHighest = Color(0xFF26222B),
    outline = Color(0xFF3A3640),
    outlineVariant = Color(0xFF26222B),
    error = Danger,
)

@Composable
fun InboxTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = colors, content = content)
