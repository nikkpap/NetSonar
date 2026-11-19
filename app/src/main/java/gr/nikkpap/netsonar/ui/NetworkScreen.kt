package gr.nikkpap.netsonar.ui

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.nikkpap.netsonar.MainViewModel
import gr.nikkpap.netsonar.i18n.LocalStrings
import gr.nikkpap.netsonar.i18n.Strings
import gr.nikkpap.netsonar.net.IfaceInfo
import gr.nikkpap.netsonar.net.IfaceType
import gr.nikkpap.netsonar.net.NetSnapshot
import gr.nikkpap.netsonar.net.NetUtil
import gr.nikkpap.netsonar.net.WanInfo

private data class Tile(val icon: ImageVector, val label: String, val value: String, val color: Color, val copyText: String? = value)

@Composable
fun NetworkScreen(vm: MainViewModel, requestPerms: () -> Unit) {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val copy: (String) -> Unit = { v ->
        clip.setText(AnnotatedString(v))
        Toast.makeText(ctx, "${s.copied}: $v", Toast.LENGTH_SHORT).show()
    }
    val snap = vm.snapshot

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader("NetSonar", s.tagline) {
                FilledTonalIconButton(onClick = { vm.forceRefresh(); vm.scanWifi() }) {
                    Icon(Icons.Rounded.Refresh, s.refresh)
                }
            }
        }
        if (!vm.hasLocationPerm) item {
            HintCard(Icons.Rounded.LocationOn, s.permTitle, s.permText, s.permButton, onClick = requestPerms)
        } else if (!vm.locationOn) item {
            HintCard(Icons.Rounded.LocationOn, s.locOffTitle, s.locOffText, s.locOffButton) {
                try { ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } catch (_: Exception) { }
            }
        }

        if (snap == null) {
            item { Text(s.loading, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            return@LazyColumn
        }

        item { HeroCard(snap, s) }

        val lanIfaces = snap.ifaces.filter { it.type == IfaceType.WIFI || it.type == IfaceType.ETHERNET }
        if (lanIfaces.isEmpty() && snap.ifaces.none { it.type == IfaceType.CELLULAR }) {
            item { HintCard(Icons.Rounded.WifiOff, s.notConnected, s.notConnectedHint, null, Accent.red) }
        }

        lanIfaces.forEach { iface ->
            val isWifi = iface.type == IfaceType.WIFI
            val color = linkColor(iface.link)
            item(key = "lbl_${iface.name}") {
                SectionLabel(
                    if (isWifi) "${s.wifi} · ${snap.wifi?.ssid ?: s.hiddenSsid}" else s.ethernet,
                    if (isWifi) Icons.Rounded.Wifi else Icons.Rounded.SettingsEthernet, color,
                )
            }
            val tiles = ifaceTiles(iface, if (isWifi) snap else null, s, color)
            items(tiles.chunked(2)) { row -> TileRow(row, copy) }
        }

        item { SectionLabel(s.internet, Icons.Rounded.Public, Accent.violet) }
        val w = vm.wan
        val wanTiles = wanTiles(w, vm.wanLoading, s)
        items(wanTiles.chunked(2)) { row -> TileRow(row, copy) }

        item {
            Text(
                s.tapToCopy, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun TileRow(row: List<Tile>, copy: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        row.forEach { t ->
            InfoTile(Modifier.weight(1f), t.icon, t.label, t.value, t.color, onClick = t.copyText?.let { c -> { copy(c) } })
        }
        if (row.size == 1) Spacer(Modifier.weight(1f))
    }
}

private fun ifaceTiles(i: IfaceInfo, snapForWifi: NetSnapshot?, s: Strings, c: Color): List<Tile> = buildList {
    add(Tile(Icons.Rounded.PhoneAndroid, s.localIp, i.ipv4 ?: s.unavailable, c, i.ipv4))
    add(Tile(Icons.Rounded.Router, s.gateway, i.gateway ?: s.unavailable, Accent.green, i.gateway))
    add(Tile(Icons.Rounded.AccountTree, s.subnet, "${NetUtil.prefixToMask(i.prefix)}\n/${i.prefix}", Accent.blue, NetUtil.prefixToMask(i.prefix)))
    add(Tile(Icons.Rounded.Dns, s.dns, i.dns.take(3).joinToString("\n").ifEmpty { s.unavailable }, Accent.violet, i.dns.joinToString(", ").ifEmpty { null }))
    add(Tile(Icons.Rounded.Fingerprint, s.myMac, i.mac ?: s.macHidden, Accent.pink, i.mac))
    val wf = snapForWifi?.wifi
    if (wf != null) {
        add(Tile(Icons.Rounded.Router, s.routerMac, wf.bssid ?: s.unavailable, Accent.amber, wf.bssid))
        add(Tile(Icons.Rounded.Wifi, s.band, wf.link?.let { linkLabel(it, s) } ?: "—", c, null))
        add(Tile(Icons.Rounded.GraphicEq, s.channel, "${wf.channel}  ·  ${wf.freq} MHz", c, null))
        add(Tile(Icons.Rounded.Speed, s.linkSpeed, if (wf.linkSpeed > 0) "${wf.linkSpeed} Mbps" else "—", Accent.green, null))
        add(Tile(Icons.Rounded.Memory, s.standard, wf.standard ?: "—", Accent.blue, null))
    }
    i.ipv6.firstOrNull()?.let { add(Tile(Icons.Rounded.Language, s.ipv6, it, Accent.violet, it)) }
    add(Tile(Icons.Rounded.SettingsEthernet, s.iface, i.name ?: "—", Accent.blue, null))
}

private fun wanTiles(w: WanInfo?, loading: Boolean, s: Strings): List<Tile> {
    val ph = if (loading) s.loading else s.unavailable
    val loc = listOfNotNull(w?.city, w?.country).joinToString(", ").ifEmpty { null }
    return listOf(
        Tile(Icons.Rounded.Public, s.publicIp, w?.ip ?: ph, Accent.cyan, w?.ip),
        Tile(Icons.Rounded.Business, s.isp, w?.isp ?: ph, Accent.violet, w?.isp),
        Tile(Icons.Rounded.LocationOn, s.location, loc ?: ph, Accent.pink, loc),
    )
}

@Composable
private fun HeroCard(snap: NetSnapshot, s: Strings) {
    val p = snap.primary
    val wf = snap.wifi
    val isWifi = p?.type == IfaceType.WIFI
    val color = when (p?.type) {
        IfaceType.WIFI -> linkColor(p?.link)
        IfaceType.ETHERNET -> Accent.green
        IfaceType.CELLULAR -> Accent.violet
        null -> Accent.red
    }
    GlassCard(borderBrush = Brush.linearGradient(listOf(color, Accent.violet.copy(alpha = 0.5f), Color.Transparent)), padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                IconBubble(
                    when (p?.type) {
                        IfaceType.WIFI -> Icons.Rounded.Wifi
                        IfaceType.ETHERNET -> Icons.Rounded.SettingsEthernet
                        IfaceType.CELLULAR -> Icons.Rounded.SignalCellularAlt
                        null -> Icons.Rounded.WifiOff
                    }, color, 52.dp,
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    when (p?.type) {
                        IfaceType.WIFI -> wf?.ssid ?: s.hiddenSsid
                        IfaceType.ETHERNET -> s.ethernet
                        IfaceType.CELLULAR -> s.mobileData
                        null -> s.notConnected
                    },
                    fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isWifi && wf?.link != null) {
                        Pill(linkLabel(wf.link, s), color, Icons.Rounded.Wifi)
                        Pill("${s.channel} ${wf.channel}", color)
                    }
                    if (p?.type == IfaceType.ETHERNET) Pill(s.wired, color, Icons.Rounded.CheckCircle)
                    p?.ipv4?.let { Pill(it, Accent.blue) }
                }
            }
            Spacer(Modifier.width(12.dp))
            if (isWifi && wf != null) {
                val pct = NetUtil.signalPercent(wf.rssi)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SignalGauge(pct, qualityLabel(pct, s))
                    Text("${wf.rssi} dBm", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (p?.type == IfaceType.ETHERNET) {
                SignalGauge(100, s.wired)
            }
        }
    }
}
