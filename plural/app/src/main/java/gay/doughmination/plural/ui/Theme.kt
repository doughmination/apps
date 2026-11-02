package gay.doughmination.plural.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import gay.doughmination.plural.R

// The website's tokens (system/src/lib/styles/theme.css), which are
// doughmination.gay's.
val Bg = Color(0xFF0B0A0D)
val TextColour = Color(0xFFECE9EF)
val Muted = Color(0xFFA49EAB)
val Accent = Color(0xFFEB459E)
val AccentDim = Color(0xFFEB459E).copy(alpha = 0.14f)
val AccentLine = Color(0xFFEB459E).copy(alpha = 0.4f)
val Ok = Color(0xFF7FD98B)
val Warn = Color(0xFFF2C46D)
val Bad = Color(0xFFFF8A9A)
/** A card on the backdrop, like --raised. */
val Raised = Color.White.copy(alpha = 0.035f)
/** Hairline borders, like --line. */
val Line = Color.White.copy(alpha = 0.09f)

/** IBM Plex Sans, as on the website (the same files as TTF, shared with Inbox). */
val PlexSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_bold, FontWeight.Bold),
)

private fun TextStyle.plex() = copy(fontFamily = PlexSans)

private val typography = Typography().run {
    Typography(
        displayLarge = displayLarge.plex(),
        displayMedium = displayMedium.plex(),
        displaySmall = displaySmall.plex(),
        headlineLarge = headlineLarge.plex(),
        headlineMedium = headlineMedium.plex().copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.plex().copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.plex().copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.plex().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.plex(),
        bodyLarge = bodyLarge.plex(),
        bodyMedium = bodyMedium.plex(),
        bodySmall = bodySmall.plex(),
        labelLarge = labelLarge.plex(),
        labelMedium = labelMedium.plex(),
        labelSmall = labelSmall.plex(),
    )
}

/** The website's mono eyebrows ("// system.members"). */
val Eyebrow = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    letterSpacing = 0.5.sp,
    color = Accent,
)

private val colours = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1A0611),
    primaryContainer = Color(0xFF3A1A2C),
    onPrimaryContainer = Color(0xFFFFD8EA),
    secondaryContainer = Color(0xFF2A1A24),
    onSecondaryContainer = Color(0xFFFFD8EA),
    // Screens sit on the Backdrop, so these are see-through. Sheets, menus and
    // dialogs use the surfaceContainer colours and stay opaque.
    background = Color.Transparent,
    onBackground = TextColour,
    surface = Color.Transparent,
    onSurface = TextColour,
    surfaceVariant = Color(0xFF1C1A20),
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Bg,
    surfaceContainerLow = Color(0xFF131116),
    surfaceContainer = Color(0xFF17151B),
    surfaceContainerHigh = Color(0xFF1E1B22),
    surfaceContainerHighest = Color(0xFF26222B),
    outline = Color(0xFF3A3640),
    outlineVariant = Color(0xFF26222B),
    error = Bad,
)

/** The site's wallpaper behind every screen, dimmed the way the web does it. */
@Composable
fun Backdrop(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Bg)) {
        Image(
            painterResource(R.drawable.background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.45f,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Bg.copy(alpha = 0.55f), 0.5f to Bg.copy(alpha = 0.85f), 1f to Bg),
            ),
        )
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Accent.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.2f, size.height * 0.15f),
                        radius = size.maxDimension * 0.6f,
                    ),
                )
            },
        )
    }
}

@Composable
fun SystemTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = colours, typography = typography, content = content)
