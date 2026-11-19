package gr.nikkpap.netsonar.net

import android.content.Context
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface

object NetInfoReader {

    @Suppress("DEPRECATION")
    fun read(ctx: Context): NetSnapshot {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val wm = ctx.applicationContext.getSystemService(WifiManager::class.java)
        val wifi = try { readWifi(wm) } catch (_: Exception) { null }
        val list = mutableListOf<IfaceInfo>()
        for (n in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(n) ?: continue
            val lp = cm.getLinkProperties(n) ?: continue
            val type = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> null
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> IfaceType.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> IfaceType.ETHERNET
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> IfaceType.CELLULAR
                else -> null
            } ?: continue
            val v4 = lp.linkAddresses.firstOrNull { it.address is Inet4Address }
            val v6 = lp.linkAddresses.filter { it.address is Inet6Address }
                .mapNotNull { it.address.hostAddress?.substringBefore('%') }
            val gw = lp.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
                ?.gateway?.hostAddress
            val dns = lp.dnsServers.mapNotNull { it.hostAddress?.substringBefore('%') }
            val mac = try {
                lp.interfaceName?.let { NetworkInterface.getByName(it)?.hardwareAddress }?.let { NetUtil.formatMac(it) }
            } catch (_: Exception) { null }
            val link = when (type) {
                IfaceType.WIFI -> wifi?.link ?: Link.WIFI24
                IfaceType.ETHERNET -> Link.LAN
                IfaceType.CELLULAR -> null
            }
            list += IfaceInfo(
                type = type,
                name = lp.interfaceName,
                ipv4 = v4?.address?.hostAddress,
                prefix = v4?.prefixLength ?: 24,
                gateway = gw,
                dns = dns,
                ipv6 = v6,
                mac = mac?.takeIf { it != "02:00:00:00:00:00" && it != "00:00:00:00:00:00" },
                network = n,
                link = link,
            )
        }
        val sorted = list.sortedBy { it.type.ordinal }
        return NetSnapshot(sorted, wifi.takeIf { sorted.any { it.type == IfaceType.WIFI } })
    }

    @Suppress("DEPRECATION")
    private fun readWifi(wm: WifiManager?): WifiDetails? {
        if (wm == null || !wm.isWifiEnabled) return null
        val info = wm.connectionInfo ?: return null
        if (info.frequency <= 0 && info.networkId == -1) return null
        val ssid = info.ssid?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" && it != "0x" }
        val bssid = info.bssid?.uppercase()
            ?.takeIf { it != "02:00:00:00:00:00" && it != "00:00:00:00:00:00" }
        val std = if (Build.VERSION.SDK_INT >= 30) when (info.wifiStandard) {
            1 -> "Legacy (802.11a/b/g)"
            4 -> "Wi-Fi 4 (802.11n)"
            5 -> "Wi-Fi 5 (802.11ac)"
            6 -> "Wi-Fi 6 (802.11ax)"
            7 -> "802.11ad"
            8 -> "Wi-Fi 7 (802.11be)"
            else -> null
        } else null
        return WifiDetails(
            ssid = ssid,
            bssid = bssid,
            rssi = info.rssi,
            freq = info.frequency,
            link = NetUtil.freqToLink(info.frequency),
            channel = NetUtil.freqToChannel(info.frequency),
            linkSpeed = info.linkSpeed,
            standard = std,
        )
    }

    fun locationEnabled(ctx: Context): Boolean = try {
        val lm = ctx.getSystemService(LocationManager::class.java)
        if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
        else lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    } catch (_: Exception) { true }
}
