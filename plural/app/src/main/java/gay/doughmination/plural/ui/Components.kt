@file:OptIn(ExperimentalMaterial3Api::class)

package gay.doughmination.plural.ui

import gay.doughmination.design.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gay.doughmination.plural.data.MemberStatus
import gay.doughmination.plural.data.MentalLevel
import gay.doughmination.plural.data.MentalState
import gay.doughmination.plural.data.findPrideFlag
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The website's .panel: a raised card with a hairline border. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Raised)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** A page's heading: an eyebrow over a title, as Inbox and the websites do it. */
@Composable
fun PageHead(eyebrow: String, title: String, subtitle: String? = null) {
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(eyebrow.uppercase(), style = Eyebrow)
        Text(title, style = MaterialTheme.typography.headlineMedium)
        subtitle?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
fun SectionTitle(text: String, colour: Color = TextColour) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = colour)
}

/** A pill, like the website's .chip; `active` fills it with the accent. */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    tint: Color? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(50)
    val border = when {
        active -> AccentLine
        tint != null -> tint.copy(alpha = 0.5f)
        else -> Line
    }
    Row(
        modifier
            .clip(shape)
            .background(if (active) AccentDim else (tint?.copy(alpha = 0.12f) ?: Raised))
            .border(1.dp, border, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke()
        Text(
            text,
            fontSize = 13.sp,
            color = if (active) Accent else TextColour,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A small accent badge ("fronting", "mutual", "Owner"). */
@Composable
fun Badge(text: String, accent: Boolean = true) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (accent) Accent else Muted,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (accent) AccentDim else Raised)
            .border(1.dp, if (accent) AccentLine else Line, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** A member's status note: emoji and text. */
@Composable
fun StatusTag(status: MemberStatus, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Raised)
            .border(1.dp, Line, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        status.emoji?.ifBlank { null }?.let { Text(it, fontSize = 13.sp) }
        Text(status.text, fontSize = 13.sp, color = TextColour, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** A pride flag as a small striped swatch; the accent when the label isn't a known flag. */
@Composable
fun PrideSwatch(label: String, modifier: Modifier = Modifier) {
    val stripes = findPrideFlag(label)?.stripes
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier
            .size(width = 18.dp, height = 12.dp)
            .clip(shape)
            .border(1.dp, Line, shape)
            .background(
                if (stripes == null) {
                    Brush.verticalGradient(listOf(Accent, Accent))
                } else {
                    // Hard bands, top to bottom, like the website's gradient.
                    val stops = stripes.flatMapIndexed { index, colour ->
                        listOf(index.toFloat() / stripes.size to colour, (index + 1f) / stripes.size to colour)
                    }
                    Brush.verticalGradient(*stops.toTypedArray())
                },
            ),
    )
}

/** The colour the website gives a mental-state level: ok, warn or bad. */
fun mentalColour(level: String): Color = when (MentalLevel.of(level)?.severity) {
    1 -> Warn
    2 -> Danger
    else -> Ok
}

fun mentalIcon(level: String): ImageVector = when (MentalLevel.of(level)) {
    MentalLevel.Safe -> Icons.Outlined.CheckCircle
    MentalLevel.Unstable -> Icons.Outlined.WarningAmber
    MentalLevel.Idealizing -> Icons.Outlined.Info
    MentalLevel.SelfHarming -> Icons.Outlined.Report
    MentalLevel.HighlyAtRisk -> Icons.Outlined.Shield
    null -> Icons.Outlined.Info
}

/** The home page's mental-state banner. */
@Composable
fun MentalBanner(state: MentalState, modifier: Modifier = Modifier) {
    val colour = mentalColour(state.level)
    val label = MentalLevel.of(state.level)?.label ?: state.level
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colour.copy(alpha = 0.08f))
            .border(1.dp, colour.copy(alpha = 0.45f), shape)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(mentalIcon(state.level), contentDescription = null, tint = colour, modifier = Modifier.size(30.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row {
                Text("status: ", color = Muted, fontFamily = Mono, fontSize = 14.sp)
                Text("[ $label ]", color = colour, fontWeight = FontWeight.SemiBold, fontFamily = Mono, fontSize = 14.sp)
            }
            state.notes?.ifBlank { null }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            formatTime(state.updatedAt)?.let {
                Text("last_updated: $it", color = Muted, fontSize = 12.sp, fontFamily = Mono)
            }
        }
    }
}

/** A plain notice line, like the website's .notice. */
@Composable
fun Notice(text: String, error: Boolean = false, modifier: Modifier = Modifier) {
    val colour = if (error) Danger else Muted
    Text(
        text,
        color = if (error) Danger else TextColour,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colour.copy(alpha = 0.08f))
            .border(1.dp, colour.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(12.dp),
    )
}

/** The top bar for screens opened on top of a tab. */
@Composable
fun BackBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        // AppRoot already pads for the status bar.
        windowInsets = WindowInsets(0),
    )
}

/** A labelled row of content in a form. */
@Composable
fun Field(label: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Muted)
        content()
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
    }
}

@Composable
fun Gap(height: Int = 8) = Spacer(Modifier.height(height.dp))

@Composable
fun HGap(width: Int = 8) = Spacer(Modifier.width(width.dp))

private val timeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

/** An ISO timestamp from the API in the phone's time zone and locale. */
fun formatTime(iso: String?): String? = iso?.let {
    runCatching { timeFormat.format(Instant.parse(it).atZone(ZoneId.systemDefault())) }.getOrNull()
}

/** "for 3h 12m", from an ISO timestamp to now. */
fun sinceText(iso: String?): String? = iso?.let {
    runCatching {
        val seconds = (System.currentTimeMillis() - Instant.parse(it).toEpochMilli()) / 1000
        duration(seconds.toDouble())
    }.getOrNull()
}

/** The website's duration format: "3h 12m", or "2d 4h" past a day. */
fun duration(seconds: Double): String {
    val hours = (seconds / 3600).toLong()
    val minutes = ((seconds % 3600) / 60).toLong()
    return if (hours > 24) "${hours / 24}d ${hours % 24}h" else "${hours}h ${minutes}m"
}
