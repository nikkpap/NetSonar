package gr.nikkpap.netsonar.net

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

object Http {
    fun get(url: String, timeout: Int = 8000, accept: String = "*/*"): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeout
        c.readTimeout = timeout
        c.setRequestProperty("User-Agent", "NetSonar-Android")
        c.setRequestProperty("Accept", accept)
        try {
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}

object Wan {
    suspend fun fetch(): WanInfo? = withContext(Dispatchers.IO) {
        try {
            val j = JSONObject(Http.get("https://ipinfo.io/json", accept = "application/json"))
            WanInfo(
                ip = j.optString("ip").ifBlank { null },
                isp = j.optString("org").ifBlank { null }?.replace(Regex("^AS\\d+\\s+"), ""),
                city = j.optString("city").ifBlank { null },
                country = j.optString("country").ifBlank { null },
            )
        } catch (_: Exception) {
            try { WanInfo(Http.get("https://api.ipify.org").trim(), null, null, null) } catch (_: Exception) { null }
        }
    }
}

/** MAC → manufacturer, via api.macvendors.com (1 request/second), cached forever on the phone. */
object Vendor {
    private val mem = ConcurrentHashMap<String, String>()
    private val mutex = Mutex()
    private var last = 0L

    /** Offline IEEE database first (instant, no internet), then macvendors.com. */
    suspend fun lookup(ctx: Context, mac: String): String? {
        if (NetUtil.isRandomMac(mac)) return null
        OuiDb.find(ctx, mac)?.let { return it.first }
        return lookupOnline(ctx, mac)
    }

    suspend fun lookupOnline(ctx: Context, mac: String): String? {
        val oui = mac.uppercase().replace(":", "").replace("-", "").take(6)
        if (oui.length < 6) return null
        mem[oui]?.let { return it.ifEmpty { null } }
        val sp = ctx.getSharedPreferences("oui_cache", Context.MODE_PRIVATE)
        sp.getString(oui, null)?.let { mem[oui] = it; return it.ifEmpty { null } }
        return mutex.withLock {
            val cached = mem[oui]
            if (cached != null) return@withLock cached.ifEmpty { null }
            val wait = 1100 - (System.currentTimeMillis() - last)
            if (wait > 0) delay(wait)
            val name: String? = withContext(Dispatchers.IO) {
                try {
                    Http.get("https://api.macvendors.com/$oui", 6000).trim().take(60)
                } catch (e: Exception) {
                    if (e.message?.contains("404") == true) "" else null
                }
            }
            last = System.currentTimeMillis()
            if (name != null) {
                mem[oui] = name
                sp.edit().putString(oui, name).apply()
            }
            name?.ifEmpty { null }
        }
    }
}
