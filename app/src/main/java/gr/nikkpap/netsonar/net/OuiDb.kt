package gr.nikkpap.netsonar.net

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.zip.GZIPInputStream

data class MacInfo(
    val mac: String,
    val vendor: String?,
    val country: String?,
    val source: String?,      // "IEEE" (offline) or "macvendors.com"
    val randomized: Boolean,  // locally administered = private/random MAC
    val multicast: Boolean,
)

/** Offline IEEE OUI database (MA-L / MA-M / MA-S), bundled in assets/oui.txt.gz. */
object OuiDb {
    private var map: HashMap<String, Pair<String, String>>? = null
    private val mutex = Mutex()

    private suspend fun load(ctx: Context): HashMap<String, Pair<String, String>> = mutex.withLock {
        map ?: withContext(Dispatchers.IO) {
            val m = HashMap<String, Pair<String, String>>(60000)
            ctx.assets.open("oui.txt.gz").use { raw ->
                GZIPInputStream(raw).bufferedReader().forEachLine { line ->
                    val p = line.split('\t')
                    if (p.size >= 2) m[p[0]] = p[1] to p.getOrElse(2) { "" }
                }
            }
            m
        }.also { map = it }
    }

    /** "3c-22-fb-12:34.56" → "3C22FB123456" or null if not 12 hex digits. */
    fun normalize(input: String): String? {
        val hex = input.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }
        return if (hex.length == 12) hex else null
    }

    fun pretty(hex12: String) = hex12.chunked(2).joinToString(":")

    /** Longest-prefix match: MA-S (36 bit) → MA-M (28 bit) → MA-L (24 bit). */
    suspend fun find(ctx: Context, mac: String): Pair<String, String>? {
        val hex = normalize(mac) ?: return null
        val m = load(ctx)
        return m[hex.take(9)] ?: m[hex.take(7)] ?: m[hex.take(6)]
    }

    suspend fun info(ctx: Context, input: String, online: Boolean = true): MacInfo? {
        val hex = normalize(input) ?: return null
        val first = hex.take(2).toInt(16)
        val random = (first and 0x02) != 0
        val multi = (first and 0x01) != 0
        val off = find(ctx, hex)
        if (off != null) return MacInfo(pretty(hex), off.first, off.second.ifBlank { null }, "IEEE", random, multi)
        val on = if (online && !random) Vendor.lookupOnline(ctx, pretty(hex)) else null
        return MacInfo(pretty(hex), on, null, on?.let { "macvendors.com" }, random, multi)
    }
}
