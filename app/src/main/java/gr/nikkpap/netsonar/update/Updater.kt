package gr.nikkpap.netsonar.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import gr.nikkpap.netsonar.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(val version: String, val notes: String, val apkUrl: String?, val pageUrl: String)

/** OTA updates from the public GitHub repo's latest Release (first .apk asset). */
object Updater {
    const val REPO = "nikkpap/NetSonar"
    const val REPO_URL = "https://github.com/$REPO"

    suspend fun latest(): ReleaseInfo = withContext(Dispatchers.IO) {
        val j = JSONObject(
            Http.get("https://api.github.com/repos/$REPO/releases/latest", 10000, "application/vnd.github+json")
        )
        var apk: String? = null
        val assets = j.optJSONArray("assets")
        if (assets != null) for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                apk = a.optString("browser_download_url"); break
            }
        }
        ReleaseInfo(
            version = j.optString("tag_name").trim().removePrefix("v").removePrefix("V"),
            notes = j.optString("body").trim(),
            apkUrl = apk,
            pageUrl = j.optString("html_url").ifBlank { "$REPO_URL/releases" },
        )
    }

    fun isNewer(remote: String, local: String): Boolean {
        val r = parts(remote); val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun parts(v: String) = v.split('.', '-', '_').mapNotNull { p -> p.filter { it.isDigit() }.toIntOrNull() }

    fun canInstall(ctx: Context) =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    suspend fun download(ctx: Context, url: String, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "NetSonar_update.apk")
        var c = URL(url).openConnection() as HttpURLConnection
        var hops = 0
        while (true) { // follow GitHub's redirect to its CDN
            c.instanceFollowRedirects = false
            c.connectTimeout = 15000; c.readTimeout = 30000
            c.setRequestProperty("User-Agent", "NetSonar-Android")
            val code = c.responseCode
            if (code in 300..399 && hops < 6) {
                val loc = c.getHeaderField("Location") ?: throw IOException("Bad redirect")
                c.disconnect(); hops++
                c = URL(URL(c.url, loc).toString()).openConnection() as HttpURLConnection
                continue
            }
            if (code !in 200..299) throw IOException("HTTP $code")
            break
        }
        val total = c.contentLengthLong
        var lastPct = -1
        c.inputStream.use { inp ->
            out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                var read = 0L
                while (true) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n); read += n
                    if (total > 0) {
                        val pct = (read * 100 / total).toInt()
                        if (pct != lastPct) { lastPct = pct; onProgress(pct / 100f) }
                    }
                }
            }
        }
        c.disconnect()
        out
    }

    fun install(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
