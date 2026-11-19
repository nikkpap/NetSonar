package gr.nikkpap.netsonar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.nikkpap.netsonar.MainViewModel
import gr.nikkpap.netsonar.i18n.LocalStrings
import gr.nikkpap.netsonar.i18n.Strings
import gr.nikkpap.netsonar.net.AccessPoint
import gr.nikkpap.netsonar.net.Link
import gr.nikkpap.netsonar.net.NetUtil
import kotlin.math.abs

private val Palette = listOf(
    Color(0xFF22D3EE), Color(0xFFA78BFA), Color(0xFFF59E0B), Color(0xFF34D399), Color(0xFFF472B6),
    Color(0xFF60A5FA), Color(0xFFFB7185), Color(0xFFA3E635), Color(0xFFFBBF24), Color(0xFF2DD4BF),
)

private fun apColor(ap: AccessPoint) = Palette[((ap.ssid ?: ap.bssid).hashCode() and 0x7fffffff) % Palette.size]

/** Least-crowded channel among the non-overlapping ones of the band. */
fun bestChannel(aps: List<AccessPoint>, link: Link): Int? {
    val candidates = when (link) {
        Link.WIFI24 -> listOf(1, 6, 11)
        Link.WIFI5 -> listOf(36, 40, 44, 48, 149, 153, 157, 161)
        Link.WIFI6 -> listOf(5, 21, 37, 53, 69, 85, 101, 117)
        Link.LAN -> return null
    }
    val band = aps.filter { it.link == link }
    return candidates.minByOrNull { ch ->
        val cf = NetUtil.channelToFreq(ch, link)
        band.sumOf { ap ->
            val reach = ap.widthMhz / 2.0 + 10.0
            val overlap = reach - abs(ap.centerFreq - cf)
            if (overlap > 0) (ap.level + 100).coerceAtLeast(1) * overlap / reach else 0.0
        }
    }
}

@Composable
fun WifiScreen(vm: MainViewModel, requestPerms: () -> Unit) {
    val s = LocalStrings.current
    var filter by rememberSaveable { mutableStateOf<Link?>(null) }
    LaunchedEffect(Unit) { vm.scanWifi() }
    val aps = vm.aps
    val shown = aps.filter { filter == null || it.link == filter }
    val options = buildList<Link?> {
        add(null); add(Link.WIFI24); add(Link.WIFI5)
        if (aps.any { it.link == Link.WIFI6 }) add(Link.WIFI6)
    }
    val inf = rememberInfiniteTransition(label = "spin")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "spin")

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenHeader(s.wifiNearby, "${s.wifiSubtitle} · ${s.networksCount(aps.size)}") {
                FilledTonalButton(onClick = { vm.scanWifi() }) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp).rotate(if (vm.wifiScanning) spin else 0f))
                    Spacer(Modifier.width(6.dp)); Text(s.wifiScan)
                }
            }
        }
        if (!vm.hasLocationPerm) item {
            HintCard(Icons.Rounded.LocationOn, s.permTitle, s.permText, s.permButton, onClick = requestPerms)
        }
        if (!vm.wifiEnabled) item { HintCard(Icons.Rounded.WifiOff, s.wifi, s.wifiOff, null, Accent.red) }
        item {
            LinkFilterRow(filter, options, s, { l -> if (l == null) aps.size else aps.count { it.link == l } }) { filter = it }
        }
        val bands = (if (filter == null) listOf(Link.WIFI24, Link.WIFI5, Link.WIFI6) else listOfNotNull(filter))
            .filter { b -> aps.any { it.link == b } }
        if (bands.isNotEmpty()) item {
            GlassCard(borderBrush = Brush.linearGradient(listOf(Accent.amber, Accent.green.copy(alpha = 0.3f)))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Rounded.Lightbulb, Accent.amber, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.bestChannel, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(s.bestChannelHint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.size(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    bands.forEach { b ->
                        bestChannel(aps, b)?.let { ch ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$ch", fontSize = 30.sp, fontWeight = FontWeight.Black, color = linkColor(b))
                                Text(linkLabel(b, s), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        bands.forEach { b ->
            item(key = "spec_$b") {
                GlassCard {
                    SectionLabel("${s.spectrum} · ${linkLabel(b, s)}", null, linkColor(b))
                    Spacer(Modifier.size(8.dp))
                    SpectrumChart(shown.filter { it.link == b }, b)
                }
            }
        }
        if (aps.isEmpty() && vm.wifiEnabled) item {
            Text(s.noWifiResults, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(shown, key = { it.bssid + it.freq }) { ap -> ApRow(ap, s) }
        if (vm.wifiThrottled) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timer, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(s.throttled, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SpectrumChart(aps: List<AccessPoint>, link: Link) {
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val anim = remember(link) { Animatable(0f) }
    LaunchedEffect(link, aps.size) { anim.snapTo(0f); anim.animateTo(1f, tween(800)) }
    val (fMin, fMax, ticks) = when (link) {
        Link.WIFI24 -> Triple(2400f, 2495f, listOf(1, 3, 5, 7, 9, 11, 13))
        Link.WIFI5 -> Triple(5150f, 5895f, listOf(36, 52, 100, 116, 132, 149, 165))
        Link.WIFI6 -> Triple(5925f, 7125f, listOf(1, 33, 65, 97, 129, 161, 193, 225))
        Link.LAN -> return
    }
    val small = TextStyle(fontSize = 10.sp, color = label)
    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        val left = 30.dp.toPx()
        val right = size.width - 6.dp.toPx()
        val top = 14.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        fun x(f: Float) = left + (f - fMin) / (fMax - fMin) * (right - left)
        fun y(dbm: Int) = bottom - ((dbm + 100).coerceIn(0, 75) / 75f) * (bottom - top)
        for (d in listOf(-90, -70, -50, -30)) {
            drawLine(grid, Offset(left, y(d)), Offset(right, y(d)), 1f)
            drawText(measurer, "$d", Offset(0f, y(d) - 7.dp.toPx()), small)
        }
        drawLine(grid, Offset(left, bottom), Offset(right, bottom), 2f)
        ticks.forEach { ch ->
            val xx = x(NetUtil.channelToFreq(ch, link).toFloat())
            val lay = measurer.measure("$ch", small)
            drawText(lay, topLeft = Offset(xx - lay.size.width / 2f, bottom + 3.dp.toPx()))
        }
        aps.sortedBy { it.level }.forEach { ap ->
            val col = apColor(ap)
            val half = ap.widthMhz / 2f + 1f
            val x0 = x(ap.centerFreq - half).coerceAtLeast(left)
            val x1 = x(ap.centerFreq + half).coerceAtMost(right)
            if (x1 <= x0) return@forEach
            val peak = bottom - (bottom - y(ap.level)) * anim.value
            val ctrl = bottom - (bottom - peak) / 0.75f
            val path = Path().apply {
                moveTo(x0, bottom)
                cubicTo(x0 + (x1 - x0) * 0.12f, ctrl, x1 - (x1 - x0) * 0.12f, ctrl, x1, bottom)
                close()
            }
            drawPath(path, Brush.verticalGradient(listOf(col.copy(alpha = 0.35f), col.copy(alpha = 0.04f)), peak, bottom))
            drawPath(path, col, style = Stroke(if (ap.connected) 3.dp.toPx() else 1.6.dp.toPx()))
            if (ap.level > -82) {
                val name = (ap.ssid ?: "•").let { if (it.length > 14) it.take(13) + "…" else it }
                val lay = measurer.measure(name, TextStyle(fontSize = 10.sp, color = col, fontWeight = FontWeight.Bold))
                val tx = ((x0 + x1) / 2f - lay.size.width / 2f).coerceIn(left, right - lay.size.width)
                drawText(lay, topLeft = Offset(tx, (peak - lay.size.height - 2.dp.toPx()).coerceAtLeast(0f)))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApRow(ap: AccessPoint, s: Strings) {
    val lc = linkColor(ap.link)
    val pct = NetUtil.signalPercent(ap.level)
    GlassCard(
        Modifier.fillMaxWidth(), padding = 14.dp,
        borderBrush = if (ap.connected) Brush.linearGradient(listOf(Accent.green, Accent.green.copy(alpha = 0.2f))) else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(48.dp)) {
                SignalBars(ap.level, Modifier.size(26.dp))
                Text("${ap.level}", fontSize = 11.sp, fontFamily = Mono, color = qualityColor(pct), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        ap.ssid ?: s.hiddenNetwork, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                        color = if (ap.ssid == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    if (ap.connected) { Spacer(Modifier.width(6.dp)); Pill(s.connected, Accent.green, Icons.Rounded.CheckCircle) }
                }
                Text(ap.bssid, fontFamily = Mono, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(5.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Pill(linkLabel(ap.link, s), lc, linkIcon(ap.link))
                    Pill("Ch ${ap.channel}", lc)
                    Pill("${ap.widthMhz} MHz", Accent.blue)
                    if (ap.security.isEmpty()) Pill(s.open, Accent.red, Icons.Rounded.LockOpen)
                    else Pill(ap.security, Accent.green, Icons.Rounded.Lock)
                }
            }
        }
    }
}
