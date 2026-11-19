package gr.nikkpap.netsonar.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DeviceUnknown
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.nikkpap.netsonar.i18n.Strings
import gr.nikkpap.netsonar.net.DeviceKind
import gr.nikkpap.netsonar.net.Link

val Mono = FontFamily.Monospace

fun linkColor(l: Link?): Color = when (l) {
    Link.WIFI24 -> Accent.amber
    Link.WIFI5 -> Accent.cyan
    Link.WIFI6 -> Accent.violet
    Link.LAN -> Accent.green
    null -> Accent.blue
}

fun linkLabel(l: Link, s: Strings) = when (l) {
    Link.WIFI24 -> "2.4 GHz"
    Link.WIFI5 -> "5 GHz"
    Link.WIFI6 -> "6 GHz"
    Link.LAN -> s.lan
}

fun linkIcon(l: Link?): ImageVector = if (l == Link.LAN) Icons.Rounded.SettingsEthernet else Icons.Rounded.Wifi

fun kindIcon(k: DeviceKind): ImageVector = when (k) {
    DeviceKind.ROUTER -> Icons.Rounded.Router
    DeviceKind.PHONE -> Icons.Rounded.Smartphone
    DeviceKind.COMPUTER -> Icons.Rounded.Computer
    DeviceKind.TV -> Icons.Rounded.Tv
    DeviceKind.PRINTER -> Icons.Rounded.Print
    DeviceKind.CAMERA -> Icons.Rounded.Videocam
    DeviceKind.IOT -> Icons.Rounded.Memory
    DeviceKind.UNKNOWN -> Icons.Rounded.DeviceUnknown
}

fun kindLabel(k: DeviceKind, s: Strings) = when (k) {
    DeviceKind.ROUTER -> s.kRouter
    DeviceKind.PHONE -> s.kPhone
    DeviceKind.COMPUTER -> s.kComputer
    DeviceKind.TV -> s.kTv
    DeviceKind.PRINTER -> s.kPrinter
    DeviceKind.CAMERA -> s.kCamera
    DeviceKind.IOT -> s.kIot
    DeviceKind.UNKNOWN -> s.kUnknown
}

fun qualityColor(pct: Int): Color = when {
    pct >= 75 -> Accent.green
    pct >= 50 -> Accent.cyan
    pct >= 30 -> Accent.amber
    else -> Accent.red
}

fun qualityLabel(pct: Int, s: Strings) = when {
    pct >= 75 -> s.qExcellent
    pct >= 50 -> s.qGood
    pct >= 30 -> s.qFair
    else -> s.qWeak
}

@Composable
fun AppBackground() {
    val dark = LocalDark.current
    val bg = MaterialTheme.colorScheme.background
    Box(
        Modifier
            .fillMaxSize()
            .background(bg)
            .drawBehind {
                val a = if (dark) 0.20f else 0.12f
                val c1 = Offset(size.width * 0.95f, size.height * 0.02f)
                drawCircle(Brush.radialGradient(listOf(Accent.cyan.copy(alpha = a), Color.Transparent), c1, size.width), size.width, c1)
                val c2 = Offset(size.width * 0.0f, size.height * 0.72f)
                drawCircle(Brush.radialGradient(listOf(Accent.violet.copy(alpha = a * 0.8f), Color.Transparent), c2, size.width * 0.9f), size.width * 0.9f, c2)
            }
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderBrush: Brush? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    var m = modifier
        .clip(shape)
        .background(cs.surface.copy(alpha = if (LocalDark.current) 0.74f else 0.94f))
        .border(1.dp, borderBrush ?: SolidColor(cs.outline.copy(alpha = 0.6f)), shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(padding), content = content)
}

@Composable
fun GradientTitle(text: String, size: Int = 30) {
    Text(
        text,
        style = TextStyle(
            brush = Brush.linearGradient(listOf(Accent.cyan, Accent.violet, Accent.pink)),
            fontSize = size.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp,
        )
    )
}

@Composable
fun ScreenHeader(title: String, subtitle: String, action: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            GradientTitle(title)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
        action()
    }
}

@Composable
fun SectionLabel(text: String, icon: ImageVector? = null, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(Modifier.padding(top = 6.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text.uppercase(), color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
    }
}

@Composable
fun Pill(text: String, color: Color, icon: ImageVector? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)) }
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun IconBubble(icon: ImageVector, color: Color, diameter: Dp = 44.dp) {
    Box(
        Modifier.size(diameter).clip(CircleShape)
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.10f)))),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = color, modifier = Modifier.size(diameter * 0.5f)) }
}

@Composable
fun InfoTile(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    mono: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    GlassCard(modifier, onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.size(6.dp))
        Text(
            value, fontFamily = if (mono) Mono else null, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface, maxLines = 4, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun HintCard(icon: ImageVector, title: String, text: String, button: String?, color: Color = Accent.amber, onClick: () -> Unit = {}) {
    GlassCard(borderBrush = Brush.linearGradient(listOf(color.copy(alpha = 0.8f), color.copy(alpha = 0.2f)))) {
        Row(verticalAlignment = Alignment.Top) {
            IconBubble(icon, color, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.size(4.dp))
                Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
                if (button != null) {
                    Spacer(Modifier.size(10.dp))
                    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Accent.ink)) {
                        Text(button, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Filter chips: All / 2.4 / 5 / 6 / LAN, each with a live count. */
@Composable
fun LinkFilterRow(selected: Link?, options: List<Link?>, s: Strings, count: (Link?) -> Int, onSelect: (Link?) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { l ->
            val sel = l == selected
            val c = if (l == null) MaterialTheme.colorScheme.primary else linkColor(l)
            val label = if (l == null) s.filterAll else linkLabel(l, s)
            Row(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (sel) c else c.copy(alpha = 0.12f))
                    .border(1.dp, c.copy(alpha = if (sel) 1f else 0.35f), RoundedCornerShape(50))
                    .clickable { onSelect(l) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (l != null) {
                    Icon(linkIcon(l), null, tint = if (sel) Accent.ink else c, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                }
                Text(label, color = if (sel) Accent.ink else c, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (sel) Accent.ink.copy(alpha = 0.18f) else c.copy(alpha = 0.18f))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) { Text("${count(l)}", color = if (sel) Accent.ink else c, fontSize = 11.sp, fontWeight = FontWeight.Black) }
            }
        }
    }
}

/** Equal-width segmented selector. */
@Composable
fun ChoiceRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceVariant.copy(alpha = 0.6f)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (key, label) ->
            val sel = key == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (sel) Brush.linearGradient(listOf(cs.primary, cs.secondary)) else SolidColor(Color.Transparent))
                    .clickable { onSelect(key) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (sel) Accent.ink else cs.onSurface, maxLines = 1)
            }
        }
    }
}

@Composable
fun SignalGauge(percent: Int, label: String, diameter: Dp = 100.dp) {
    val anim by animateFloatAsState(percent / 100f, tween(1000), label = "gauge")
    val color = qualityColor(percent)
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val st = 9.dp.toPx()
            val tl = Offset(st / 2, st / 2)
            val sz = Size(this.size.width - st, this.size.height - st)
            drawArc(track, 135f, 270f, false, tl, sz, style = Stroke(st, cap = StrokeCap.Round))
            drawArc(
                Brush.sweepGradient(listOf(color.copy(alpha = 0.6f), color, color.copy(alpha = 0.6f))),
                135f, 270f * anim, false, tl, sz, style = Stroke(st, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$percent%", fontWeight = FontWeight.Black, fontSize = 22.sp, color = color)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 4 tiny bars, for Wi-Fi lists. */
@Composable
fun SignalBars(dbm: Int, modifier: Modifier = Modifier.size(22.dp)) {
    val pct = gr.nikkpap.netsonar.net.NetUtil.signalPercent(dbm)
    val bars = when { pct >= 75 -> 4; pct >= 50 -> 3; pct >= 25 -> 2; else -> 1 }
    val on = qualityColor(pct)
    val off = MaterialTheme.colorScheme.outline
    Canvas(modifier) {
        val w = size.width / 4f
        for (i in 0 until 4) {
            val h = size.height * (i + 1) / 4f
            drawRoundRect(
                if (i < bars) on else off,
                topLeft = Offset(i * w + w * 0.15f, size.height - h),
                size = Size(w * 0.7f, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.3f),
            )
        }
    }
}
