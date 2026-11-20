package gr.nikkpap.netsonar.net

import android.net.Network

/** How a device / access point is reached. */
enum class Link { WIFI24, WIFI5, WIFI6, LAN }

enum class IfaceType { WIFI, ETHERNET, CELLULAR }

data class IfaceInfo(
    val type: IfaceType,
    val name: String?,
    val ipv4: String?,
    val prefix: Int,
    val gateway: String?,
    val dns: List<String>,
    val ipv6: List<String>,
    val mac: String?,
    val network: Network?,
    val link: Link?,
)

data class WifiDetails(
    val ssid: String?,
    val bssid: String?,
    val rssi: Int,
    val freq: Int,
    val link: Link?,
    val channel: Int,
    val linkSpeed: Int,
    val standard: String?,
)

data class NetSnapshot(val ifaces: List<IfaceInfo>, val wifi: WifiDetails?) {
    val scannable: List<IfaceInfo>
        get() = ifaces.filter { (it.type == IfaceType.WIFI || it.type == IfaceType.ETHERNET) && it.ipv4 != null }
    val primary: IfaceInfo?
        get() = scannable.firstOrNull { it.type == IfaceType.WIFI }
            ?: scannable.firstOrNull()
            ?: ifaces.firstOrNull()
}

data class WanInfo(val ip: String?, val isp: String?, val city: String?, val country: String?)

enum class DeviceKind { ROUTER, PHONE, COMPUTER, TV, PRINTER, CAMERA, IOT, UNKNOWN }

data class Device(
    val ip: String,
    val sortKey: Long,
    val link: Link,
    val hostname: String? = null,
    val mac: String? = null,
    val vendor: String? = null,
    val latencyMs: Int? = null,
    val openPorts: List<Int> = emptyList(),
    val isGateway: Boolean = false,
    val isSelf: Boolean = false,
    val kind: DeviceKind = DeviceKind.UNKNOWN,
    val model: String? = null,
    val services: List<String> = emptyList(),
    val macFromBssid: Boolean = false,
) {
    val key: String get() = "$link|$ip"
}

data class AccessPoint(
    val ssid: String?,
    val bssid: String,
    val level: Int,
    val freq: Int,
    val centerFreq: Int,
    val link: Link,
    val channel: Int,
    val widthMhz: Int,
    val security: String,
    val connected: Boolean,
)

object NetUtil {
    fun freqToLink(f: Int): Link? = when (f) {
        in 2400..2500 -> Link.WIFI24
        in 4900..5924 -> Link.WIFI5
        in 5925..7125 -> Link.WIFI6
        else -> null
    }

    fun freqToChannel(f: Int): Int = when {
        f == 2484 -> 14
        f in 2400..2483 -> (f - 2407) / 5
        f in 4900..5924 -> (f - 5000) / 5
        f in 5925..7125 -> (f - 5950) / 5
        else -> 0
    }

    fun channelToFreq(ch: Int, link: Link): Int = when (link) {
        Link.WIFI24 -> if (ch == 14) 2484 else 2407 + ch * 5
        Link.WIFI5 -> 5000 + ch * 5
        Link.WIFI6 -> 5950 + ch * 5
        Link.LAN -> 0
    }

    private val ipv4Regex = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    fun isIpv4(s: String) = ipv4Regex.matches(s)

    fun ipToLong(ip: String): Long =
        ip.split('.').fold(0L) { acc, p -> (acc shl 8) or ((p.toLongOrNull() ?: 0L) and 0xFF) }

    fun longToIp(v: Long) = "${(v shr 24) and 0xFF}.${(v shr 16) and 0xFF}.${(v shr 8) and 0xFF}.${v and 0xFF}"

    fun maskOf(prefix: Int): Long = if (prefix <= 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL

    fun prefixToMask(prefix: Int): String = longToIp(maskOf(prefix))

    fun sameSubnet(a: String, b: String, prefix: Int): Boolean {
        val m = maskOf(prefix)
        return (ipToLong(a) and m) == (ipToLong(b) and m)
    }

    fun formatMac(b: ByteArray?): String? =
        if (b == null || b.size != 6) null else b.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }

    /** Locally administered bit set = private / randomized MAC. */
    fun isRandomMac(mac: String): Boolean =
        ((mac.take(2).toIntOrNull(16) ?: 0) and 0x02) != 0

    fun signalPercent(dbm: Int): Int = ((dbm + 100) * 100 / 55).coerceIn(0, 100)
}
