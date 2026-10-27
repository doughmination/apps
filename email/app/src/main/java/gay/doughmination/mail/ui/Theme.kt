package gay.doughmination.mail.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import gay.doughmination.mail.R
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The site's palette (email/src/web/app.css).
val Accent = Color(0xFFEB459E)
val Danger = Color(0xFFFF5C7A)
val Muted = Color(0xFFA49EAB)
val Bg = Color(0xFF0B0A0D)
/** A card on the backdrop, like --raised on the web. */
val Raised = Color.White.copy(alpha = 0.05f)

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A1A2C),
    onPrimaryContainer = Color(0xFFFFD8EA),
    secondaryContainer = Color(0xFF2A1A24),
    onSecondaryContainer = Color(0xFFFFD8EA),
    // Screens and app bars sit on the Backdrop, so these are see-through.
    // Sheets, menus and dialogs use the surfaceContainer colours and stay opaque.
    background = Color.Transparent,
    onBackground = Color(0xFFECE9EF),
    surface = Color.Transparent,
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

/**
 * The site's wallpaper behind every screen, dimmed the way the web does it:
 * the art at half strength under a dark wash and a soft pink glow.
 */
@Composable
fun Backdrop(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Bg)) {
        Image(
            painterResource(R.drawable.background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.5f,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Bg.copy(alpha = 0.55f),
                    0.55f to Bg.copy(alpha = 0.8f),
                    1f to Bg,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Accent.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.2f, size.height * 0.3f),
                        radius = size.maxDimension * 0.6f,
                    ),
                )
            },
        )
    }
}

@Composable
fun InboxTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = colors, content = content)
