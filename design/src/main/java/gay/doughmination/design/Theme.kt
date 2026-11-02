package gay.doughmination.design

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The Doughmination palette, as Inbox and the websites use it
// (email/src/web/app.css, doughmination.gay).
val Accent = Color(0xFFEB459E)
val Danger = Color(0xFFFF5C7A)
val Muted = Color(0xFFA49EAB)
val Bg = Color(0xFF0B0A0D)
val TextColour = Color(0xFFECE9EF)
/** Success and caution, for status dots and banners. */
val Ok = Color(0xFF7FD98B)
val Warn = Color(0xFFF2C46D)
/** A card on the backdrop, like --raised on the web. */
val Raised = Color.White.copy(alpha = 0.05f)
/** Hairline borders, like --line on the web. */
val Line = Color.White.copy(alpha = 0.09f)
/** Selected chips and tabs: the accent, faint. */
val AccentDim = Accent.copy(alpha = 0.14f)
val AccentLine = Accent.copy(alpha = 0.4f)

/** IBM Plex Sans, as on the web and doughmination.gay (the same files, as TTF). */
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
        headlineMedium = headlineMedium.plex(),
        headlineSmall = headlineSmall.plex(),
        titleLarge = titleLarge.plex(),
        titleMedium = titleMedium.plex(),
        titleSmall = titleSmall.plex(),
        bodyLarge = bodyLarge.plex(),
        bodyMedium = bodyMedium.plex(),
        bodySmall = bodySmall.plex(),
        labelLarge = labelLarge.plex(),
        labelMedium = labelMedium.plex(),
        labelSmall = labelSmall.plex(),
    )
}

/** The web's small uppercase accent label (.eyebrow), used for headings. Pass it uppercase text. */
val Eyebrow = TextStyle(
    fontFamily = PlexSans,
    fontSize = 11.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 1.5.sp,
    color = Accent,
)

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
    error = Danger,
)

/**
 * Every app's Compose theme. Put a [Backdrop] behind the screens.
 *
 * Surfaces are see-through, so content outside a Scaffold or Surface would
 * otherwise get Compose's default text colour, black. Light text is the
 * default everywhere instead.
 */
@Composable
fun DoughminationTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = colors, typography = typography) {
        CompositionLocalProvider(LocalContentColor provides TextColour, content = content)
    }

/**
 * Edge to edge with light system-bar icons. The apps are always dark, so the
 * icons must not follow the phone's light/dark setting. Call before super.onCreate.
 */
fun ComponentActivity.enableDoughminationEdgeToEdge() = enableEdgeToEdge(
    statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
    navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
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

val SectionShape = RoundedCornerShape(16.dp)

/** A card like the web's sections (Inbox's settings): dark glass, a hairline border, an eyebrow heading. */
@Composable
fun GlassSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(SectionShape)
            .background(Bg.copy(alpha = 0.72f))
            .border(1.dp, Line, SectionShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, Modifier.size(16.dp), tint = Accent)
                    Spacer(Modifier.width(6.dp))
                }
                Text(title.uppercase(), style = Eyebrow)
            }
        }
        content()
    }
}
