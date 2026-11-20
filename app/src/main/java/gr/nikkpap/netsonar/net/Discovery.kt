package gr.nikkpap.netsonar.net

import android.content.Context
import android.net.Network
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** What a device says about itself over mDNS (Bonjour) / UPnP (SSDP). */
class DiscInfo(val ip: String) {
    @Volatile var hostname: String? = null      // e.g. "Nikos-iPhone"
    @Volatile var friendly: String? = null      // e.g. "Living Room TV"
    @Volatile var model: String? = null         // e.g. "QE55Q60" / "Chromecast Ultra"
    @Volatile var manufacturer: String? = null  // from UPnP
    @Volatile var mac: String? = null           // some devices announce it (AirPlay, workstation)
    val services: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val bestName: String? get() = friendly ?: hostname
}

object Discovery {

    private val MDNS_SERVICES = listOf(
        "_services._dns-sd._udp.local", "_googlecast._tcp.local", "_airplay._tcp.local", "_raop._tcp.local",
        "_companion-link._tcp.local", "_device-info._tcp.local", "_ipp._tcp.local", "_ipps._tcp.local",
        "_printer._tcp.local", "_pdl-datastream._tcp.local", "_scanner._tcp.local", "_uscan._tcp.local",
        "_http._tcp.local", "_https._tcp.local", "_smb._tcp.local", "_afpovertcp._tcp.local",
        "_workstation._tcp.local", "_ssh._tcp.local", "_hap._tcp.local", "_homekit._tcp.local",
        "_spotify-connect._tcp.local", "_sonos._tcp.local", "_amzn-wplay._tcp.local", "_androidtvremote2._tcp.local",
        "_adb-tls-connect._tcp.local", "_matter._tcp.local", "_esphomelib._tcp.local", "_shelly._tcp.local",
        "_miio._udp.local", "_mqtt._tcp.local", "_daap._tcp.local", "_nvstream._tcp.local",
    )

    private val NAME_SERVICES = setOf(
        "airplay", "raop", "companion-link", "ipp", "ipps", "printer", "pdl-datastream", "scanner", "uscan",
        "smb", "afpovertcp", "workstation", "sonos", "spotify-connect", "hap", "homekit", "amzn-wplay",
        "androidtvremote2", "daap", "nvstream", "esphomelib", "shelly",
    )

    suspend fun run(ctx: Context, network: Network?, ips: List<String>, ms: Int = 3500): Map<String, DiscInfo> = coroutineScope {
        val out = ConcurrentHashMap<String, DiscInfo>()
        fun info(ip: String) = out.getOrPut(ip) { DiscInfo(ip) }
        val wm = ctx.applicationContext.getSystemService(WifiManager::class.java)
        val lock = try { wm?.createMulticastLock("netsonar")?.apply { setReferenceCounted(false); acquire() } } catch (_: Exception) { null }
        try {
            listOf(
                async(Dispatchers.IO) { mdnsUnicast(network, ips, ms, ::info) },
                async(Dispatchers.IO) { mdnsMulticastListen(ms, ::info) },
                async(Dispatchers.IO) { ssdp(network, ms, ::info) },
            ).awaitAll()
        } finally {
            try { lock?.release() } catch (_: Exception) { }
        }
        out.filterValues { it.bestName != null || it.mac != null || it.model != null || it.manufacturer != null || it.services.isNotEmpty() }
    }

    // ------------------------------------------------------------------ mDNS
    private fun query(names: List<String>, type: Int): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(byteArrayOf(0, 0, 0, 0, (names.size shr 8).toByte(), names.size.toByte(), 0, 0, 0, 0, 0, 0))
        for (n in names) {
            for (label in n.split('.')) {
                val b = label.toByteArray(Charsets.UTF_8)
                o.write(b.size); o.write(b)
            }
            o.write(0)
            o.write(type shr 8); o.write(type and 0xFF)
            o.write(0x80); o.write(0x01) // class IN + "unicast response please" bit
        }
        return o.toByteArray()
    }

    private fun reverseName(ip: String) = ip.split('.').reversed().joinToString(".") + ".in-addr.arpa"

    /** Browse service types (multicast, legacy-unicast replies) + ask every host for its own name. */
    private fun mdnsUnicast(network: Network?, ips: List<String>, ms: Int, info: (String) -> DiscInfo) {
        try {
            DatagramSocket().use { s ->
                if (network != null) try { network.bindSocket(s) } catch (_: Exception) { }
                s.soTimeout = 250
                val group = InetAddress.getByName("224.0.0.251")
                val browse = MDNS_SERVICES.chunked(10).map { query(it, 12) }
                repeat(2) { browse.forEach { q -> try { s.send(DatagramPacket(q, q.size, group, 5353)) } catch (_: Exception) { } } }
                for (ip in ips) {
                    try {
                        val q = query(listOf(reverseName(ip)), 12)
                        s.send(DatagramPacket(q, q.size, InetAddress.getByName(ip), 5353))
                        val b2 = query(MDNS_SERVICES.take(10), 12)
                        s.send(DatagramPacket(b2, b2.size, InetAddress.getByName(ip), 5353))
                    } catch (_: Exception) { }
                }
                receiveLoop(s, ms) { buf, len, src -> parseDns(buf, len, src, info) }
            }
        } catch (_: Exception) { }
    }

    /** Also listen on 5353 for devices that only answer by multicast. */
    private fun mdnsMulticastListen(ms: Int, info: (String) -> DiscInfo) {
        try {
            val s = MulticastSocket(null)
            s.reuseAddress = true
            s.bind(InetSocketAddress(5353))
            @Suppress("DEPRECATION") s.joinGroup(InetAddress.getByName("224.0.0.251"))
            s.soTimeout = 250
            s.use { receiveLoop(it, ms) { buf, len, src -> parseDns(buf, len, src, info) } }
        } catch (_: Exception) { }
    }

    private inline fun receiveLoop(s: DatagramSocket, ms: Int, onPacket: (ByteArray, Int, String) -> Unit) {
        val end = System.currentTimeMillis() + ms
        val buf = ByteArray(9000)
        while (System.currentTimeMillis() < end) {
            val p = DatagramPacket(buf, buf.size)
            try {
                s.receive(p)
                val src = p.address?.hostAddress ?: continue
                if (!NetUtil.isIpv4(src)) continue
                onPacket(buf.copyOf(p.length), p.length, src)
            } catch (_: SocketTimeoutException) {
            } catch (_: Exception) { break }
        }
    }

    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    private fun readName(b: ByteArray, start: Int): Pair<String, Int> {
        val labels = ArrayList<String>()
        var pos = start; var jumped = false; var next = -1; var guard = 0
        while (pos < b.size && guard++ < 128) {
            val len = b[pos].toInt() and 0xFF
            if (len == 0) { pos++; break }
            if ((len and 0xC0) == 0xC0) {
                if (pos + 1 >= b.size) break
                val ptr = ((len and 0x3F) shl 8) or (b[pos + 1].toInt() and 0xFF)
                if (!jumped) next = pos + 2
                jumped = true; pos = ptr; continue
            }
            pos++
            if (pos + len > b.size) break
            labels += String(b, pos, len, Charsets.UTF_8); pos += len
        }
        return labels.joinToString(".") to (if (jumped) next else pos)
    }

    private fun clean(host: String) = host.removeSuffix(".").removeSuffix(".local").takeIf { it.isNotBlank() }

    private fun parseDns(b: ByteArray, len: Int, src: String, info: (String) -> DiscInfo) {
        try {
            if (len < 12) return
            if ((b[2].toInt() and 0x80) == 0) return // not a response
            val qd = u16(b, 4); val rr = u16(b, 6) + u16(b, 8) + u16(b, 10)
            var pos = 12
            repeat(qd) { pos = readName(b, pos).second + 4 }
            val d = info(src)
            val aRecords = HashMap<String, String>()
            repeat(rr) {
                if (pos + 10 > len) return
                val (name, p) = readName(b, pos)
                val type = u16(b, p); val rdLen = u16(b, p + 8); val rd = p + 10
                if (rd + rdLen > len) return
                when (type) {
                    1 -> if (rdLen == 4) aRecords[name] = (0 until 4).joinToString(".") { (b[rd + it].toInt() and 0xFF).toString() }
                    12 -> {
                        val target = readName(b, rd).first
                        if (name.endsWith("in-addr.arpa")) {
                            if (d.hostname == null) d.hostname = clean(target)
                        } else if (!name.startsWith("_services")) {
                            val svcFull = name.removeSuffix(".")
                            val svc = svcFull.removeSuffix(".local").substringBefore("._tcp").substringBefore("._udp").removePrefix("_")
                            d.services += svc
                            var inst = target.removeSuffix(".").removeSuffix("." + svcFull).trim()
                            when {
                                svc == "raop" && inst.contains("@") -> {          // "AABBCCDDEEFF@Living Room"
                                    val hex = inst.substringBefore("@")
                                    if (hex.length == 12 && d.mac == null) d.mac = hex.chunked(2).joinToString(":").uppercase()
                                    inst = inst.substringAfter("@")
                                }
                                svc == "workstation" -> {                          // "nas [aa:bb:cc:dd:ee:ff]"
                                    Regex("\\[([0-9A-Fa-f:]{17})]").find(inst)?.let { if (d.mac == null) d.mac = it.groupValues[1].uppercase() }
                                    inst = inst.substringBefore(" [")
                                }
                            }
                            if (d.friendly == null && inst.isNotBlank() && inst.length < 60 && svc in NAME_SERVICES) d.friendly = inst
                        }
                    }
                    33 -> { val target = readName(b, rd + 6).first; if (d.hostname == null) d.hostname = clean(target) }
                    16 -> {
                        var i = rd; val end = rd + rdLen
                        while (i < end) {
                            val l = b[i].toInt() and 0xFF; i++
                            if (l == 0 || i + l > end) { i += l; continue }
                            val kv = String(b, i, l, Charsets.UTF_8); i += l
                            val k = kv.substringBefore('=').lowercase(); val v = kv.substringAfter('=', "").trim()
                            if (v.isEmpty()) continue
                            when (k) {
                                "fn" -> d.friendly = v                                   // Chromecast / Google TV name
                                "md", "model", "am", "mdl", "usb_mdl" -> if (d.model == null) d.model = v
                                "ty", "product" -> if (d.model == null) d.model = v.trim('(', ')')
                                "manufacturer", "usb_mfg", "mfg" -> if (d.manufacturer == null) d.manufacturer = v
                            }
                        }
                    }
                }
                pos = rd + rdLen
            }
            aRecords.forEach { (host, ip) -> if (ip == src && d.hostname == null) d.hostname = clean(host) }
        } catch (_: Exception) { }
    }

    // ------------------------------------------------------------------ UPnP / SSDP
    private fun ssdp(network: Network?, ms: Int, info: (String) -> DiscInfo) {
        val locations = HashMap<String, String>()
        try {
            DatagramSocket().use { s ->
                if (network != null) try { network.bindSocket(s) } catch (_: Exception) { }
                s.soTimeout = 250
                val group = InetAddress.getByName("239.255.255.250")
                for (st in listOf("ssdp:all", "upnp:rootdevice")) {
                    val msg = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $st\r\n\r\n"
                        .toByteArray()
                    repeat(2) { try { s.send(DatagramPacket(msg, msg.size, group, 1900)) } catch (_: Exception) { } }
                }
                receiveLoop(s, (ms - 1200).coerceAtLeast(1500)) { buf, len, src ->
                    val text = String(buf, 0, len)
                    val loc = Regex("(?im)^location:\\s*(\\S+)").find(text)?.groupValues?.get(1)
                    val server = Regex("(?im)^server:\\s*(.+)$").find(text)?.groupValues?.get(1)?.trim()
                    val d = info(src)
                    d.services += "upnp"
                    if (loc != null && (locations[src] == null || loc.contains("rootDesc", true))) locations[src] = loc
                    if (server != null && d.model == null && server.length < 80) d.model = server
                }
            }
        } catch (_: Exception) { }
        for ((src, loc) in locations) {
            try {
                val url = URL(loc)
                val c = (if (network != null) network.openConnection(url) else url.openConnection()) as HttpURLConnection
                c.connectTimeout = 1500; c.readTimeout = 2000
                val xml = c.inputStream.bufferedReader().use { it.readText() }
                c.disconnect()
                fun tag(t: String) = Regex("<$t>\\s*([^<]{1,80}?)\\s*</$t>", RegexOption.IGNORE_CASE).find(xml)?.groupValues?.get(1)
                val d = info(src)
                tag("friendlyName")?.let { d.friendly = d.friendly ?: it }
                tag("manufacturer")?.let { d.manufacturer = it }
                val model = listOfNotNull(tag("modelName"), tag("modelNumber")).distinct().joinToString(" ").ifBlank { null }
                if (model != null) d.model = model
                tag("deviceType")?.let { dt -> d.services += dt.substringAfter("device:").substringBefore(":").lowercase() }
            } catch (_: Exception) { }
        }
    }
}
