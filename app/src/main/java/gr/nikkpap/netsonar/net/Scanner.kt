package gr.nikkpap.netsonar.net

import android.net.Network
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

sealed interface ScanEvent {
    data class Progress(val done: Int, val total: Int) : ScanEvent
    data class Found(val device: Device) : ScanEvent
}

data class Enrichment(val hostname: String?, val mac: String?)

class Scanner {

    companion object {
        val QUICK_PORTS = intArrayOf(80, 443, 22, 445, 139, 53, 62078, 8080)
        val SLOW_PORTS = intArrayOf(554, 9100, 631, 3389, 7000, 8008, 21, 5000)
        val PORT_NAMES = mapOf(
            21 to "FTP", 22 to "SSH", 53 to "DNS", 80 to "HTTP", 139 to "NetBIOS", 443 to "HTTPS",
            445 to "SMB", 554 to "RTSP", 631 to "IPP", 3389 to "RDP", 5000 to "UPnP/NAS",
            7000 to "AirPlay", 8008 to "Cast", 8080 to "HTTP-alt", 9100 to "Print", 62078 to "iOS",
        )
        val WEB_PORTS = listOf(80, 8080, 443)

        fun guessKind(d: Device): DeviceKind {
            if (d.isGateway) return DeviceKind.ROUTER
            if (d.isSelf) return DeviceKind.PHONE
            val p = d.openPorts.toSet()
            val sv = d.services.joinToString(" ").lowercase()
            fun sAny(vararg k: String) = k.any { it in sv }
            val v = (d.vendor ?: "").lowercase()
            val h = (d.hostname ?: "").lowercase()
            fun vAny(vararg k: String) = k.any { it in v }
            fun hAny(vararg k: String) = k.any { it in h }
            return when {
                sAny("ipp", "printer", "pdl-datastream", "uscan", "scanner") -> DeviceKind.PRINTER
                sAny("googlecast", "androidtvremote", "amzn-wplay", "mediarenderer", "nvstream") -> DeviceKind.TV
                sAny("internetgatewaydevice", "wlanaccesspoint") -> DeviceKind.ROUTER
                sAny("smb", "afpovertcp", "workstation", "ssh") -> DeviceKind.COMPUTER
                sAny("companion-link") -> DeviceKind.PHONE
                sAny("hap", "homekit", "esphomelib", "shelly", "matter", "miio", "mqtt") -> DeviceKind.IOT
                sAny("airplay", "raop", "sonos", "spotify-connect", "daap") -> DeviceKind.TV
                9100 in p || 631 in p || vAny("epson", "canon", "brother", "kyocera", "xerox", "lexmark", "ricoh") || hAny("printer", "print") -> DeviceKind.PRINTER
                554 in p || vAny("hikvision", "dahua", "reolink", "ezviz", "axis comm") || hAny("cam", "ipc", "nvr") -> DeviceKind.CAMERA
                8008 in p || vAny("roku", "lg electronics", "tcl", "hisense", "vestel", "sharp") || hAny("tv", "chromecast", "bravia", "firetv", "shield", "webos") -> DeviceKind.TV
                62078 in p || hAny("iphone", "ipad", "android", "galaxy", "pixel", "redmi", "phone") -> DeviceKind.PHONE
                3389 in p || 445 in p || 139 in p || hAny("desktop", "laptop", "macbook", "imac", "-pc") ||
                    vAny("intel", "dell", "lenovo", "micro-star", "asustek", "gigabyte", "hewlett", "liteon", "azurewave") -> DeviceKind.COMPUTER
                vAny("espressif", "tuya", "shelly", "sonoff", "itead", "blebox", "broadlink", "amazon", "google", "signify", "nest", "tasmota") -> DeviceKind.IOT
                vAny("apple", "samsung", "xiaomi", "huawei device", "oneplus", "oppo", "vivo", "motorola", "realme", "honor") -> DeviceKind.PHONE
                vAny("raspberry", "synology", "qnap") || 22 in p -> DeviceKind.COMPUTER
                vAny("tp-link", "netgear", "ubiquiti", "mikrotik", "zyxel", "d-link", "tenda", "cisco", "aruba", "huawei tech", "avm") -> DeviceKind.ROUTER
                else -> DeviceKind.UNKNOWN
            }
        }
    }

    private val dnsPool = Executors.newCachedThreadPool()

    // ---- diagnostics of the last scan (shown under the radar) ----
    val pingHits = AtomicInteger(0)
    val tcpHits = AtomicInteger(0)
    @Volatile var lastError: String? = null
    @Volatile var arpInfo: String = "-"
    fun resetStats() { pingHits.set(0); tcpHits.set(0); lastError = null; arpInfo = "-" }

    /** Own pool: socket probes block, Dispatchers.IO (64 threads) would make the sweep crawl. */
    private val io = Executors.newFixedThreadPool(224).asCoroutineDispatcher()

    /** Sweeps the subnet of [iface] and emits every live host. */
    fun scan(iface: IfaceInfo): Flow<ScanEvent> = channelFlow {
        val ip = iface.ipv4
        if (ip == null) return@channelFlow
        val link = iface.link ?: Link.LAN
        val selfL = NetUtil.ipToLong(ip)
        var prefix = iface.prefix.coerceIn(8, 30)
        if (prefix < 22) prefix = 24 // huge subnets: scan the /24 around us
        val mask = NetUtil.maskOf(prefix)
        val net = selfL and mask
        val bcast = net or (mask.inv() and 0xFFFFFFFFL)
        val targets = ((net + 1) until bcast).filter { it != selfL }
        val total = targets.size
        val gw = iface.gateway
        val netIf = try { iface.name?.let { NetworkInterface.getByName(it) } } catch (_: Exception) { null }
        val done = AtomicInteger(0)
        val sem = Semaphore(24)

        send(
            ScanEvent.Found(
                Device(ip, selfL, link, hostname = Build.MODEL, mac = iface.mac, latencyMs = 0,
                    isGateway = ip == gw, isSelf = true, kind = DeviceKind.PHONE)
            )
        )
        targets.map { h ->
            launch {
                sem.withPermit {
                    val hip = NetUtil.longToIp(h)
                    val r = probe(hip, iface.network, netIf)
                    if (r != null || hip == gw) {
                        send(
                            ScanEvent.Found(
                                Device(hip, h, link, latencyMs = r?.first, openPorts = r?.second ?: emptyList(),
                                    isGateway = hip == gw,
                                    kind = if (hip == gw) DeviceKind.ROUTER else DeviceKind.UNKNOWN)
                            )
                        )
                    }
                    send(ScanEvent.Progress(done.incrementAndGet(), total))
                }
            }
        }.joinAll()
    }.flowOn(Dispatchers.Default)

    private enum class Tcp { OPEN, REFUSED, NONE }

    /**
     * Phase 1: ping + a few very common ports → is the host alive?
     * Phase 2 (alive hosts only): the remaining service ports.
     * @return (latency ms, open ports) or null if the host looks dead.
     */
    private suspend fun probe(ip: String, network: Network?, netIf: NetworkInterface?): Pair<Int, List<Int>>? = coroutineScope {
        val addr = InetAddress.getByAddress(ip.split('.').map { it.toInt().toByte() }.toByteArray())
        val t0 = System.nanoTime()
        val ping = async(io) {
            try {
                val ok = if (netIf != null) addr.isReachable(netIf, 0, 700) else addr.isReachable(700)
                if (ok) ((System.nanoTime() - t0) / 1_000_000).toInt() else -1
            } catch (_: Exception) { -1 }
        }
        val first = QUICK_PORTS.map { p -> async(io) { p to tcp(addr, p, network, 500) } }.awaitAll()
        val pingMs = ping.await()
        val alive1 = first.any { it.second.first != Tcp.NONE }
        if (pingMs >= 0) pingHits.incrementAndGet()
        if (alive1) tcpHits.incrementAndGet()
        if (!alive1 && pingMs < 0) return@coroutineScope null
        val second = SLOW_PORTS.map { p -> async(io) { p to tcp(addr, p, network, 600) } }.awaitAll()
        val res = first + second
        val open = res.filter { it.second.first == Tcp.OPEN }.map { it.first }.sorted()
        val tcpMs = res.filter { it.second.first != Tcp.NONE }.minOfOrNull { it.second.second } ?: Int.MAX_VALUE
        val lat = if (pingMs >= 0) minOf(pingMs, tcpMs) else tcpMs
        lat.coerceAtMost(9999) to open
    }

    private fun tcp(addr: InetAddress, port: Int, network: Network?, timeout: Int): Pair<Tcp, Int> {
        val s = Socket()
        val t0 = System.nanoTime()
        fun ms() = ((System.nanoTime() - t0) / 1_000_000).toInt()
        return try {
            if (network != null) try { network.bindSocket(s) } catch (_: Exception) { }
            s.connect(InetSocketAddress(addr, port), timeout)
            Tcp.OPEN to ms()
        } catch (e: ConnectException) {
            val m = e.message ?: ""
            if (m.contains("ECONNREFUSED") || m.contains("refused", ignoreCase = true)) Tcp.REFUSED to ms()
            else { if (!m.contains("EHOSTUNREACH") && !m.contains("ETIMEDOUT")) lastError = m.takeLast(90); Tcp.NONE to 0 }
        } catch (e: java.net.SocketTimeoutException) {
            Tcp.NONE to 0
        } catch (e: Exception) {
            lastError = (e.javaClass.simpleName + ": " + (e.message ?: "")).take(90)
            Tcp.NONE to 0
        } finally {
            try { s.close() } catch (_: Exception) { }
        }
    }

    /** Hostname (reverse DNS / NetBIOS) and, for Windows & many smart devices, the MAC via NetBIOS. */
    suspend fun enrich(d: Device, network: Network?): Enrichment = coroutineScope {
        val nb = async(Dispatchers.IO) { netbios(d.ip, network) }
        val rdns = async(Dispatchers.IO) {
            try { dnsPool.submit<String?> { reverse(d.ip) }.get(2500, TimeUnit.MILLISECONDS) } catch (_: Exception) { null }
        }
        val n = nb.await()
        Enrichment(hostname = n?.first ?: rdns.await(), mac = n?.second)
    }

    private fun reverse(ip: String): String? = try {
        val h = InetAddress.getByName(ip).canonicalHostName
        if (h == null || h == ip) null
        else listOf(".local", ".lan", ".home", ".fritz.box", ".localdomain", ".station", ".domain")
            .fold(h) { acc, suf -> acc.removeSuffix(suf) }
    } catch (_: Exception) { null }

    private fun netbios(ip: String, network: Network?): Pair<String?, String?>? = try {
        val q = byteArrayOf(0x4E, 0x53, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0x20) +
            "CKAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".toByteArray(Charsets.US_ASCII) +
            byteArrayOf(0, 0, 0x21, 0, 1)
        DatagramSocket().use { s ->
            if (network != null) try { network.bindSocket(s) } catch (_: Exception) { }
            s.soTimeout = 900
            s.send(DatagramPacket(q, q.size, InetAddress.getByName(ip), 137))
            val buf = ByteArray(1024)
            val p = DatagramPacket(buf, buf.size)
            s.receive(p)
            val len = p.length
            if (len < 57) null else {
                val count = buf[56].toInt() and 0xFF
                var name: String? = null
                for (i in 0 until count) {
                    val o = 57 + i * 18
                    if (o + 18 > len) break
                    val suffix = buf[o + 15].toInt() and 0xFF
                    val group = ((buf[o + 16].toInt() and 0xFF) and 0x80) != 0
                    if (suffix == 0 && !group && name == null) {
                        name = String(buf, o, 15, Charsets.US_ASCII).trim().takeIf { it.isNotEmpty() }
                    }
                }
                val mo = 57 + count * 18
                val mac = if (mo + 6 <= len) {
                    val m = buf.copyOfRange(mo, mo + 6)
                    if (m.all { it == 0.toByte() }) null else NetUtil.formatMac(m)
                } else null
                name to mac
            }
        }
    } catch (_: Exception) { null }

    /** Web banner: page <title>, Server header, login realm, Chromecast info. */
    data class WebInfo(val name: String?, val model: String?, val brand: String?)

    private val BRANDS = listOf(
        "TP-Link", "Tapo", "Huawei", "ZTE", "Nokia", "Technicolor", "Sagemcom", "Zyxel", "Netgear", "ASUS", "D-Link",
        "Ubiquiti", "UniFi", "MikroTik", "Tenda", "Xiaomi", "Mi Router", "Linksys", "AVM", "FRITZ!Box", "Cisco",
        "Hikvision", "Dahua", "Reolink", "EZVIZ", "Imou", "Axis", "Shelly", "Sonoff", "Tasmota", "ESPHome", "Tuya",
        "Synology", "QNAP", "WD My Cloud", "Raspberry", "OctoPrint", "Home Assistant", "Philips Hue", "IKEA",
        "Epson", "Canon", "Brother", "HP", "Kyocera", "Xerox", "Samsung", "LG", "Sony", "Panasonic", "Hisense", "TCL",
        "Philips", "Sharp", "Roku", "Fire TV", "NVIDIA", "Kodi", "Plex", "Jellyfin", "Sonos", "Bose", "Denon", "Yamaha",
        "Google", "Chromecast", "Nest", "Amazon", "Apple", "BleBox", "Broadlink", "Daikin", "Mitsubishi", "Vodafone", "COSMOTE", "Nova", "Wind",
    )

    suspend fun httpInfo(ip: String, ports: List<Int>, network: Network?): WebInfo? = withContext(Dispatchers.IO) {
        var name: String? = null; var model: String? = null; var brand: String? = null
        val blob = StringBuilder()
        if (8008 in ports) try {
            val j = org.json.JSONObject(get("http://$ip:8008/setup/eureka_info?params=name,device_info", network))
            name = j.optString("name").ifBlank { null }
            j.optJSONObject("device_info")?.let { di ->
                model = di.optString("model_name").ifBlank { null }
                brand = di.optString("manufacturer").ifBlank { null }
            }
        } catch (_: Exception) { }
        for (port in listOf(80, 8080, 8000, 8081, 5000, 8008).filter { it in ports }) {
            try {
                val url = java.net.URL(if (port == 80) "http://$ip/" else "http://$ip:$port/")
                val c = (if (network != null) network.openConnection(url) else url.openConnection()) as java.net.HttpURLConnection
                c.connectTimeout = 1500; c.readTimeout = 2000; c.instanceFollowRedirects = true
                c.setRequestProperty("User-Agent", "Mozilla/5.0 NetSonar")
                val server = c.getHeaderField("Server")
                val realm = c.getHeaderField("WWW-Authenticate")?.let { Regex("realm=\"?([^\",]+)").find(it)?.groupValues?.get(1) }
                val body = try {
                    (if (c.responseCode < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { r ->
                        val cb = CharArray(65536); val n = r.read(cb); if (n > 0) String(cb, 0, n) else ""
                    } ?: ""
                } catch (_: Exception) { "" }
                c.disconnect()
                val title = Regex("<title[^>]*>\\s*([^<]{1,80}?)\\s*</title>", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)
                    ?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotBlank() && !it.contains("404") && !it.equals("index", true) }
                blob.append(' ').append(title ?: "").append(' ').append(realm ?: "").append(' ').append(server ?: "")
                if (model == null) model = title ?: realm ?: server?.takeIf { it.length < 60 }
            } catch (_: Exception) { }
        }
        if (brand == null) {
            val t = blob.toString()
            brand = BRANDS.firstOrNull { Regex("\\b" + Regex.escape(it) + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(t) }
        }
        if (name == null && model == null && brand == null) null else WebInfo(name, model, brand)
    }

    private fun get(u: String, network: Network?): String {
        val url = java.net.URL(u)
        val c = (if (network != null) network.openConnection(url) else url.openConnection()) as java.net.HttpURLConnection
        c.connectTimeout = 1500; c.readTimeout = 2000
        return try { c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }

    /** ARP table (works on Android ≤ 9 and some TV boxes / rooted devices). */
    suspend fun readArp(): Map<String, String> = withContext(Dispatchers.IO) {
        val map = HashMap<String, String>()
        try {
            File("/proc/net/arp").readLines().drop(1).forEach { l ->
                val p = l.trim().split(Regex("\\s+"))
                if (p.size >= 4 && NetUtil.isIpv4(p[0]) && p[3].count { it == ':' } == 5 && p[3] != "00:00:00:00:00:00")
                    map[p[0]] = p[3].uppercase()
            }
        } catch (_: Exception) { }
        if (map.isNotEmpty()) arpInfo = "arp ${map.size}"
        if (map.isEmpty()) try {
            val pr = Runtime.getRuntime().exec(arrayOf("ip", "neigh", "show"))
            pr.inputStream.bufferedReader().readLines().forEach { l ->
                val p = l.trim().split(" ")
                val i = p.indexOf("lladdr")
                if (i > 0 && i + 1 < p.size && NetUtil.isIpv4(p[0])) map[p[0]] = p[i + 1].uppercase()
            }
            pr.destroy()
            arpInfo = "neigh ${map.size}"
        } catch (e: Exception) { arpInfo = "neigh ✗" }
        map
    }
}
