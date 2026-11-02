@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package gay.doughmination.plural.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gay.doughmination.plural.R
import gay.doughmination.plural.data.Couple
import gay.doughmination.plural.data.Member
import gay.doughmination.plural.data.Relationship
import gay.doughmination.plural.data.find
import gay.doughmination.plural.data.layout
import gay.doughmination.plural.data.memberColour
import gay.doughmination.plural.data.pairs
import gay.doughmination.plural.data.project
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The website's /relationships: the map, then everyone in pairs. */
@Composable
fun RelationshipsScreen(vm: SystemViewModel) {
    val couples = remember(vm.relationships, vm.members) { pairs(vm.relationships, vm.members) }

    PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PageHead("// system.relationships", "Relationship Map", "Who is dating who across the system")

            if (couples.isEmpty()) {
                Panel { Text(if (vm.loading) "Loading…" else "No relationships to show yet", color = Muted) }
                return@Column
            }

            Panel {
                RelationshipMap(vm.members, vm.relationships) { vm.open(Screen.MemberPage(it.id)) }
                Text("Drag to turn it; tap someone to open their page.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }

            Panel {
                SectionTitle("Everyone, in pairs")
                couples.forEach { couple -> CoupleRow(couple) { vm.open(Screen.MemberPage(it.id)) } }
            }
        }
    }
}

@Composable
private fun CoupleRow(couple: Couple, open: (Member) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Who(couple.a, Modifier.weight(1f), open)
            Badge(couple.label, accent = couple.edge.type != "crush")
            Who(couple.b, Modifier.weight(1f), open, end = true)
        }
        couple.edge.since?.ifBlank { null }?.let {
            Text("since $it", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun Who(member: Member, modifier: Modifier, open: (Member) -> Unit, end: Boolean = false) {
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).clickable { open(member) }.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, if (end) Alignment.End else Alignment.Start),
    ) {
        if (!end) Avatar(member.avatarUrl, 32.dp, ring = memberColour(member.color))
        // Two lines: similar long names (Serial Designation N / J) must stay tellable apart.
        Text(
            member.shownName,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = if (end) TextAlign.End else TextAlign.Start,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (end) Avatar(member.avatarUrl, 32.dp, ring = memberColour(member.color))
    }
}

// The website's RelationshipGraph.svelte, in its 720-unit square.
private const val SIZE = 720f
private const val RADIUS = 285.0
private const val NODE_RADIUS = 26f
private const val SPIN_PER_FRAME = 0.0025
private const val DRAG_SPEED = 0.008

private class Link(val edge: Relationship, val a: Int, val b: Int, val mutual: Boolean)

/** The rotating 3D map: nodes are avatars, links lines; crushes dashed, with arrows. */
@Composable
fun RelationshipMap(members: List<Member>, relationships: List<Relationship>, open: (Member) -> Unit) {
    val graph = remember(members, relationships) {
        val nodes = mutableListOf<Member>()
        fun indexOf(identifier: String): Int? {
            val member = members.find(identifier) ?: return null
            val index = nodes.indexOfFirst { it.id == member.id }
            return if (index >= 0) index else { nodes += member; nodes.size - 1 }
        }
        val links = mutableListOf<Link>()
        for (edge in relationships) {
            val a = indexOf(edge.members.getOrNull(0) ?: continue) ?: continue
            val b = indexOf(edge.members.getOrNull(1) ?: continue) ?: continue
            if (a == b) continue
            val reverse = links.indexOfFirst { edge.type == "crush" && it.edge.type == "crush" && it.a == b && it.b == a }
            if (reverse >= 0) links[reverse] = Link(links[reverse].edge, links[reverse].a, links[reverse].b, true)
            else links += Link(edge, a, b, false)
        }
        Triple(nodes.toList(), links.toList(), layout(nodes.size, links.map { it.a to it.b }, RADIUS))
    }
    val (nodes, links, points) = graph

    var yaw by remember { mutableDoubleStateOf(0.6) }
    var pitch by remember { mutableDoubleStateOf(-0.35) }
    var dragging by remember { mutableStateOf(false) }

    // A slow spin, paused while someone turns it by hand.
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { if (!dragging) yaw += SPIN_PER_FRAME }
    }

    val fallback = ImageBitmap.imageResource(R.drawable.mascot)
    val pictures = nodes.map { node -> key(node.id) { rememberPicture(node.avatarUrl) } }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = TextStyle(fontSize = 11.sp, fontFamily = PlexSans, color = TextColour)

    fun screen(index: Int, scale: Float): Placed {
        val p = project(points[index], yaw, pitch, RADIUS)
        return Placed(
            x = (SIZE / 2 + p.x.toFloat()) * scale,
            y = (SIZE / 2 + p.y.toFloat()) * scale,
            r = NODE_RADIUS * p.scale.toFloat() * scale,
            fade = 0.45f + 0.55f * p.depth.toFloat().coerceIn(0f, 1f),
            depth = p.depth,
        )
    }

    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(nodes) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, amount ->
                    change.consume()
                    val dx = amount.x / density.density
                    val dy = amount.y / density.density
                    yaw += dx * DRAG_SPEED
                    pitch = (pitch - dy * DRAG_SPEED).coerceIn(-1.3, 1.3)
                }
            }
            .pointerInput(nodes) {
                detectTapGestures { tap ->
                    val scale = size.width / SIZE
                    // The front-most node under the finger.
                    nodes.indices
                        .map { it to screen(it, scale) }
                        .filter { (_, at) -> hypot(tap.x - at.x, tap.y - at.y) <= at.r + 8.dp.toPx() }
                        .maxByOrNull { (_, at) -> at.depth }
                        ?.let { (index, _) -> open(nodes[index]) }
                }
            },
    ) {
        val scale = size.width / SIZE
        val placed = nodes.indices.map { screen(it, scale) }

        drawCircle(
            Brush.radialGradient(listOf(Accent.copy(alpha = 0.12f), Color.Transparent), center = center, radius = (RADIUS.toFloat() + 60) * scale),
            radius = (RADIUS.toFloat() + 60) * scale,
        )

        // Back to front, so nearer lines and avatars cover further ones.
        links.sortedBy { placed[it.a].depth + placed[it.b].depth }.forEach { link ->
            drawLink(link, placed[link.a], placed[link.b], scale)
        }
        nodes.indices.sortedBy { placed[it].depth }.forEach { index ->
            val at = placed[index]
            val node = nodes[index]
            drawAvatar(pictures[index].value ?: fallback, at, memberColour(node.color) ?: Accent)
            // Names for the nearer half, so the back doesn't turn to noise.
            if (at.depth > 0.35) {
                val text = measurer.measure(node.shownName, labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = androidx.compose.ui.unit.Constraints(maxWidth = (120 * scale).toInt().coerceAtLeast(1)))
                drawText(
                    text,
                    topLeft = Offset(at.x - text.size.width / 2f, at.y + at.r + 3.dp.toPx()),
                    alpha = at.fade,
                )
            }
        }
    }
}

private class Placed(val x: Float, val y: Float, val r: Float, val fade: Float, val depth: Double)

private fun DrawScope.drawLink(link: Link, from: Placed, to: Placed, scale: Float) {
    val crush = link.edge.type == "crush"
    val colour = if (crush) Warn else Accent
    val alpha = (from.fade + to.fade) / 2
    val length = max(hypot(to.x - from.x, to.y - from.y), 1f)
    // Stop a crush short of the avatar, so its arrow isn't hidden under it.
    val endGap = if (crush) (to.r + 5 * scale) / length else 0f
    val startGap = if (link.mutual) (from.r + 5 * scale) / length else 0f
    val start = Offset(from.x + (to.x - from.x) * startGap, from.y + (to.y - from.y) * startGap)
    val end = Offset(to.x - (to.x - from.x) * endGap, to.y - (to.y - from.y) * endGap)

    drawLine(
        colour.copy(alpha = alpha),
        start,
        end,
        strokeWidth = 2.2f * scale * 1.6f,
        cap = StrokeCap.Round,
        pathEffect = if (crush) PathEffect.dashPathEffect(floatArrayOf(5f * scale * 1.6f, 6f * scale * 1.6f)) else null,
    )
    if (crush) {
        arrow(end, atan2(end.y - start.y, end.x - start.x), colour.copy(alpha = alpha), scale)
        if (link.mutual) arrow(start, atan2(start.y - end.y, start.x - end.x), colour.copy(alpha = alpha), scale)
    }
}

private fun DrawScope.arrow(tip: Offset, angle: Float, colour: Color, scale: Float) {
    val size = 9f * scale * 1.6f
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(tip.x - size * cos(angle - 0.45f), tip.y - size * sin(angle - 0.45f))
        lineTo(tip.x - size * cos(angle + 0.45f), tip.y - size * sin(angle + 0.45f))
        close()
    }
    drawPath(path, colour)
}

private fun DrawScope.drawAvatar(image: ImageBitmap, at: Placed, ring: Color) {
    val clip = Path().apply { addOval(Rect(Offset(at.x, at.y), at.r)) }
    // Centre-crop to a square, as object-fit: cover would.
    val side = min(image.width, image.height)
    val src = IntOffset((image.width - side) / 2, (image.height - side) / 2)
    clipPath(clip) {
        drawRect(Bg, topLeft = Offset(at.x - at.r, at.y - at.r), size = androidx.compose.ui.geometry.Size(at.r * 2, at.r * 2))
        drawImage(
            image,
            srcOffset = src,
            srcSize = IntSize(side, side),
            dstOffset = IntOffset((at.x - at.r).toInt(), (at.y - at.r).toInt()),
            dstSize = IntSize((at.r * 2).toInt().coerceAtLeast(1), (at.r * 2).toInt().coerceAtLeast(1)),
            alpha = at.fade,
        )
    }
    drawCircle(ring.copy(alpha = at.fade), radius = at.r, center = Offset(at.x, at.y), style = Stroke(width = 2.dp.toPx()))
}
