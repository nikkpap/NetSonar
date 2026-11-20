package gr.nikkpap.netsonar.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.nikkpap.netsonar.MainViewModel
import gr.nikkpap.netsonar.i18n.LocalStrings
import gr.nikkpap.netsonar.i18n.Strings
import gr.nikkpap.netsonar.net.Device
import gr.nikkpap.netsonar.net.Link
import gr.nikkpap.netsonar.net.NetUtil
import gr.nikkpap.netsonar.net.Scanner
import kotlin.math.cos
import kotlin.math.sin

fun deviceTitle(d: Device, s: Strings): String = when {
    d.isSelf -> d.hostname ?: s.thisDevice
    !d.hostname.isNullOrBlank() -> d.hostname
    !d.vendor.isNullOrBlank() -> d.vendor
    d.isGateway -> s.router
    else -> s.unknownDevice
}

@Composable
fun DevicesScreen(vm: MainViewModel) {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    var filter by rememberSaveable { mutableStateOf<Link?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val all = vm.devices.toList()
    val q = query.trim().lowercase()
    val shown = all.filter { d ->
        (filter == null || d.link == filter) &&
            (q.isEmpty() || listOfNotNull(d.ip, d.hostname, d.mac, d.vendor).any { it.lowercase().contains(q) })
    }
    val options = buildList<Link?> {
        add(null); add(Link.WIFI24); add(Link.WIFI5)
        if (all.any { it.link == Link.WIFI6 } || vm.snapshot?.wifi?.link == Link.WIFI6) add(Link.WIFI6)
        add(Link.LAN)
    }

    // For dummies: start scanning automatically the first time the tab opens.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!vm.hasScanned && !vm.scanning && !vm.toggleScan())
            Toast.makeText(ctx, s.noNetworkToScan, Toast.LENGTH_LONG).show()
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenHeader(s.tabDevices, "${s.devicesSubtitle}  ·  v${gr.nikkpap.netsonar.BuildConfig.VERSION_NAME}") }
        item {
            Radar(all, vm.scanning, vm.progress) {
                if (!vm.toggleScan()) Toast.makeText(ctx, s.noNetworkToScan, Toast.LENGTH_LONG).show()
            }
        }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val status = when {
                    vm.scanning -> "${s.scanning}… ${(vm.progress * 100).toInt()}%"
                    !vm.hasScanned -> s.tapToScan
                    else -> s.devicesFound(all.size)
                }
                Text(status, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
                if (vm.scanning) {
                    Spacer(Modifier.size(8.dp))
                    LinearProgressIndicator(
                        progress = { vm.progress },
                        modifier = Modifier.fillMaxWidth(0.7f).height(6.dp).clip(RoundedCornerShape(50)),
                    )
                } else if (vm.identifying) {
                    Text(s.identifying, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!vm.scanning) vm.scanInfo?.let { Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, fontFamily = Mono) }
                Spacer(Modifier.size(10.dp))
                androidx.compose.material3.Button(
                    onClick = { if (!vm.toggleScan()) Toast.makeText(ctx, s.noNetworkToScan, Toast.LENGTH_LONG).show() },
                    modifier = Modifier.fillMaxWidth(0.8f).height(52.dp),
                    shape = RoundedCornerShape(50),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (vm.scanning) Accent.red else MaterialTheme.colorScheme.primary,
                        contentColor = Accent.ink,
                    ),
                ) {
                    Icon(if (vm.scanning) Icons.Rounded.Stop else Icons.Rounded.WifiTethering, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (vm.scanning) s.stop else s.scan, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.5.sp)
                }
                vm.scanError?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
            }
        }
        if (vm.scanLog.isNotEmpty()) item {
            var open by rememberSaveable { mutableStateOf(true) }
            GlassCard(Modifier.fillMaxWidth(), onClick = { open = !open }, padding = 12.dp) {
                Text("Scan log (tap to ${if (open) "hide" else "show"})", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (open) {
                    Spacer(Modifier.size(4.dp))
                    vm.scanLog.takeLast(14).forEach { line ->
                        Text(line, fontFamily = Mono, fontSize = 10.sp, lineHeight = 13.sp,
                            color = if (line.contains("ERROR") || line.contains("Nothing")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        item {
            LinkFilterRow(filter, options, s, { l -> if (l == null) all.size else all.count { it.link == l } }) { filter = it }
        }
        if (all.size > 5) item {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(s.searchHint) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, null) } },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                ),
            )
        }
        if (vm.hasScanned && !vm.scanning && all.isNotEmpty() && all.any { !it.isSelf && it.mac == null }) item {
            HintCard(Icons.Rounded.Info, s.macNoteTitle, s.macNote, null, Accent.blue)
        }
        if (vm.hasScanned && shown.isEmpty() && !vm.scanning) item {
            Text(s.noMatches, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        items(shown, key = { it.key }) { d -> DeviceRow(d, s) { selectedKey = d.key } }
        item { Spacer(Modifier.size(8.dp)) }
    }

    val sel = selectedKey?.let { k -> all.firstOrNull { it.key == k } }
    if (sel != null) DeviceSheet(sel, s) { selectedKey = null }
}

@Composable
private fun Radar(devices: List<Device>, scanning: Boolean, progress: Float, onClick: () -> Unit) {
    val s = LocalStrings.current
    val cs = MaterialTheme.colorScheme
    val inf = rememberInfiniteTransition(label = "radar")
    val angle by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "angle")
    val pulse by inf.animateFloat(0.95f, 1.05f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val blink by inf.animateFloat(0.25f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "blink")
    val sweepAlpha by animateFloatAsState(if (scanning) 1f else 0.25f, tween(600), label = "sweep")
    val primary = cs.primary

    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val c = center
            drawCircle(Brush.radialGradient(listOf(primary.copy(alpha = 0.10f), Color.Transparent), c, r), r, c)
            for (i in 1..4) drawCircle(primary.copy(alpha = 0.10f + i * 0.04f), r * i / 4f, c, style = Stroke(1.3.dp.toPx()))
            drawLine(primary.copy(alpha = 0.12f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
            drawLine(primary.copy(alpha = 0.12f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.dp.toPx())
            rotate(angle, c) {
                drawCircle(
                    Brush.sweepGradient(
                        0f to Color.Transparent, 0.72f to Color.Transparent, 1f to primary.copy(alpha = 0.55f * sweepAlpha),
                        center = c,
                    ), r, c,
                )
                drawLine(primary.copy(alpha = sweepAlpha), c, Offset(c.x + r, c.y), 2.dp.toPx())
            }
            devices.take(150).forEach { d ->
                if (d.isSelf) return@forEach
                val last = (d.sortKey and 0xFF).toInt()
                val a = Math.toRadians((((d.ip.hashCode() * 31) and 0x7fffffff) % 360).toDouble())
                val dist = if (d.isGateway) 0.30f else 0.48f + 0.46f * (last / 255f)
                val p = Offset(c.x + (cos(a) * r * dist).toFloat(), c.y + (sin(a) * r * dist).toFloat())
                val col = if (d.isGateway) Accent.green else linkColor(d.link)
                drawCircle(col.copy(alpha = 0.22f * blink), 12.dp.toPx(), p)
                drawCircle(col, 4.5.dp.toPx(), p)
            }
        }
        Box(
            Modifier
                .fillMaxSize(0.36f)
                .aspectRatio(1f)
                .scale(if (scanning) 1f else pulse)
                .shadow(28.dp, CircleShape, ambientColor = primary, spotColor = primary)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(cs.primary, cs.secondary)))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if (scanning) Icons.Rounded.Stop else Icons.Rounded.WifiTethering, null, tint = Accent.ink, modifier = Modifier.size(30.dp))
                Text(
                    if (scanning) "${(progress * 100).toInt()}%" else s.scan,
                    fontWeight = FontWeight.Black, color = Accent.ink, fontSize = 17.sp, letterSpacing = 1.5.sp,
                )
                if (scanning) Text(s.stop, fontWeight = FontWeight.Bold, color = Accent.ink.copy(alpha = 0.7f), fontSize = 10.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceRow(d: Device, s: Strings, onClick: () -> Unit) {
    val lc = linkColor(d.link)
    val kc = when {
        d.isGateway -> Accent.green
        d.isSelf -> Accent.pink
        else -> lc
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    GlassCard(
        Modifier.fillMaxWidth(), onClick = onClick, padding = 14.dp,
        borderBrush = Brush.linearGradient(listOf(kc.copy(alpha = 0.7f), kc.copy(alpha = 0.08f))),
    ) {
        // header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(kindIcon(d.kind), kc, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(deviceTitle(d, s), fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(d.ip, fontFamily = Mono, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Pill(linkLabel(d.link, s), lc, linkIcon(d.link))
                if (!d.isSelf) d.latencyMs?.let { Pill("$it ms", if (it < 50) Accent.green else if (it < 200) Accent.amber else Accent.red) }
            }
        }
        Spacer(Modifier.size(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)))
        Spacer(Modifier.size(8.dp))
        // full details
        val vend = when {
            !d.vendor.isNullOrBlank() -> d.vendor
            d.mac != null && NetUtil.isRandomMac(d.mac) -> s.randomMac
            else -> "—"
        }
        DetailLine(s.mac, d.mac ?: s.macHidden, mono = true, muted = d.mac == null)
        DetailLine(s.vendor, vend, muted = vend == "—")
        DetailLine(s.hostname, d.hostname ?: "—", muted = d.hostname == null)
        DetailLine(s.type, kindLabel(d.kind, s))
        DetailLine(s.seenVia, if (d.link == Link.LAN) s.ethernet else "${s.wifi} ${linkLabel(d.link, s)}")
        Spacer(Modifier.size(6.dp))
        if (d.openPorts.isEmpty()) {
            Text("${s.openPorts}: —", fontSize = 12.sp, color = muted)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                d.openPorts.forEach { p -> Pill("$p ${Scanner.PORT_NAMES[p] ?: ""}".trim(), Accent.violet) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (d.isGateway) Pill(s.router, Accent.green)
            if (d.isSelf) Pill(s.thisDevice, Accent.pink)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null, tint = muted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String, mono: Boolean = false, muted: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(
            value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = if (mono) Mono else null,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f), textAlign = TextAlign.End,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DeviceSheet(d: Device, s: Strings, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val uri = LocalUriHandler.current
    val copy: (String) -> Unit = { v ->
        clip.setText(AnnotatedString(v)); Toast.makeText(ctx, "${s.copied}: $v", Toast.LENGTH_SHORT).show()
    }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lc = linkColor(d.link)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(kindIcon(d.kind), if (d.isGateway) Accent.green else lc, 60.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(deviceTitle(d, s), fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(d.ip, fontFamily = Mono, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.size(16.dp))
            val rows = buildList {
                add(Triple(s.ipAddress, d.ip, true))
                add(Triple(s.hostname, d.hostname ?: "—", d.hostname != null))
                add(Triple(s.mac, d.mac ?: s.macHidden, d.mac != null))
                val vend = when {
                    d.vendor != null -> d.vendor
                    d.mac != null && NetUtil.isRandomMac(d.mac) -> s.randomMac
                    else -> "—"
                }
                add(Triple(s.vendor, vend, d.vendor != null))
                add(Triple(s.type, kindLabel(d.kind, s), false))
                add(Triple(s.latency, d.latencyMs?.let { "$it ms" } ?: "—", false))
                add(Triple(s.seenVia, if (d.link == Link.LAN) s.ethernet else "${s.wifi} ${linkLabel(d.link, s)}", false))
            }
            rows.forEach { (label, value, copyable) ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = copyable) { copy(value) }
                        .padding(vertical = 9.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.weight(0.42f))
                    Text(value, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(0.58f), textAlign = TextAlign.End)
                    if (copyable) { Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.ContentCopy, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (d.mac != null && NetUtil.isRandomMac(d.mac)) {
                Text(s.randomMacInfo, fontSize = 12.sp, color = Accent.amber, modifier = Modifier.padding(horizontal = 6.dp))
            }
            Spacer(Modifier.size(10.dp))
            SectionLabel(s.openPorts)
            Spacer(Modifier.size(6.dp))
            if (d.openPorts.isEmpty()) Text(s.noOpenPorts, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                d.openPorts.forEach { p -> Pill("$p · ${Scanner.PORT_NAMES[p] ?: "?"}", Accent.violet) }
            }
            Spacer(Modifier.size(18.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { copy(d.ip) }) { Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(s.copyIp) }
                if (d.mac != null) FilledTonalButton(onClick = { copy(d.mac) }) { Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(s.copyMac) }
                val web = Scanner.WEB_PORTS.firstOrNull { it in d.openPorts }
                if (web != null) FilledTonalButton(onClick = {
                    val url = when (web) { 443 -> "https://${d.ip}"; 80 -> "http://${d.ip}"; else -> "http://${d.ip}:$web" }
                    try { uri.openUri(url) } catch (_: Exception) { }
                }) { Icon(Icons.Rounded.OpenInBrowser, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(s.openBrowser) }
                FilledTonalButton(onClick = {
                    val text = buildString {
                        appendLine(deviceTitle(d, s))
                        appendLine("IP: ${d.ip}")
                        d.mac?.let { appendLine("MAC: $it") }
                        d.vendor?.let { appendLine("${s.vendor}: $it") }
                        if (d.openPorts.isNotEmpty()) appendLine("${s.openPorts}: ${d.openPorts.joinToString()}")
                        append("— NetSonar")
                    }
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), s.share))
                }) { Icon(Icons.Rounded.Share, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(s.share) }
            }
        }
    }
}
