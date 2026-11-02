@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package gay.doughmination.plural.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gay.doughmination.plural.data.MemberMetric
import gay.doughmination.plural.data.Timeframe

/** The website's chart colours, in order. */
private val Palette = listOf(
    0xFF8B5CF6, 0xFFEC4899, 0xFFF59E0B, 0xFF10B981, 0xFF3B82F6,
    0xFFEF4444, 0xFF14B8A6, 0xFFF97316, 0xFF6366F1, 0xFF84CC16,
).map { Color(it) }

/** The website's /user/metrics: switching and fronting over the last 30 days. */
@Composable
fun MetricsScreen(vm: SystemViewModel) {
    var timeframeName by rememberSaveable { mutableStateOf(Timeframe.Week.name) }
    val timeframe = Timeframe.valueOf(timeframeName)

    val fronting = vm.frontingMetrics
    val switches = vm.switchMetrics
    // The ten members who fronted most in the chosen window.
    val top = remember(fronting, timeframe) {
        fronting?.members?.values.orEmpty().filter { it[timeframe] > 0 }.sortedByDescending { it[timeframe] }.take(10)
    }
    val topTotal = top.sumOf { it[timeframe] }

    PullToRefreshBox(isRefreshing = vm.metricsLoading && fronting != null, onRefresh = vm::loadMetrics, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PageHead("// system.metrics", "Metrics", "System fronting and switching statistics")

            vm.metricsError?.let {
                Notice(it, error = true)
                OutlinedButton(onClick = vm::loadMetrics) { Text("Try again") }
            }

            if (fronting == null && switches == null) {
                if (vm.metricsLoading) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                return@Column
            }

            Text("Timeframe", color = Muted, style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Timeframe.entries.forEach { option ->
                    Chip(option.label, active = option == timeframe) { timeframeName = option.name }
                }
            }

            switches?.let { s ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("Switches", "${s.totalSwitches}", "Last 30 days", Modifier.weight(1f).fillMaxHeight())
                    Stat("Per day", "%.1f".format(s.averagePerDay), "On average", Modifier.weight(1f).fillMaxHeight())
                    Stat("Selected", "${s[timeframe]}", timeframe.label, Modifier.weight(1f).fillMaxHeight())
                }
            }

            if (fronting != null && top.isNotEmpty()) {
                Panel {
                    SectionTitle("Fronting time distribution")
                    Text("Top fronters for ${timeframe.label.lowercase()}", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Donut(top.map { it[timeframe] / topTotal }, Modifier.align(Alignment.CenterHorizontally).size(200.dp))
                    top.forEachIndexed { index, member ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(Palette[index % Palette.size]))
                            Text(member.shownName, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("%.0f%%".format(member[timeframe] / topTotal * 100), color = Muted)
                        }
                    }
                }
            }

            switches?.let { s ->
                Panel {
                    SectionTitle("Switch frequency over time")
                    Text("Number of switches across different timeframes", color = Muted, style = MaterialTheme.typography.bodySmall)
                    val peak = maxOf(1, Timeframe.entries.maxOf { s[it] })
                    Row(
                        Modifier.fillMaxWidth().height(180.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Timeframe.entries.forEach { option ->
                            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                Text("${s[option]}", fontSize = 12.sp)
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight((s[option].toFloat() / peak * 0.8f).coerceAtLeast(0.01f))
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(if (option == timeframe) Accent else AccentLine),
                                )
                                Text(option.key, color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (fronting != null && top.isNotEmpty()) {
                Panel {
                    SectionTitle("Top fronters")
                    Text("Most active members for ${timeframe.label.lowercase()}", color = Muted, style = MaterialTheme.typography.bodySmall)
                    top.forEachIndexed { index, member -> Ranked(index + 1, member, member[timeframe], top.first()[timeframe]) }
                }
            } else if (fronting != null) {
                Panel { Text("No fronting data available for the selected timeframe", color = Muted) }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, note: String, modifier: Modifier) {
    Panel(modifier) {
        Text(label.uppercase(), style = Eyebrow.copy(fontSize = 10.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(note, color = Muted, fontSize = 11.sp, maxLines = 2)
    }
}

/** Slices as arcs round one ring, like the website's stroke-dasharray donut. */
@Composable
private fun Donut(fractions: List<Double>, modifier: Modifier) {
    Canvas(modifier.padding(8.dp)) {
        val stroke = size.minDimension * 0.16f
        val inset = stroke / 2
        var start = -90f
        fractions.forEachIndexed { index, fraction ->
            val sweep = (fraction * 360).toFloat()
            drawArc(
                Palette[index % Palette.size],
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke),
            )
            start += sweep
        }
    }
}

@Composable
private fun Ranked(rank: Int, member: MemberMetric, seconds: Double, best: Double) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("$rank", color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.size(width = 20.dp, height = 20.dp))
        Avatar(member.avatarUrl, 34.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(member.shownName, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                HGap(6)
                Text(duration(seconds), color = Muted, fontSize = 12.sp)
            }
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Line)) {
                Box(
                    Modifier
                        .fillMaxWidth(if (best > 0) (seconds / best).toFloat().coerceIn(0f, 1f) else 0f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(Accent),
                )
            }
        }
    }
}
