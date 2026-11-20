package gr.nikkpap.netsonar

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import gr.nikkpap.netsonar.net.AccessPoint
import gr.nikkpap.netsonar.net.Device
import gr.nikkpap.netsonar.net.IfaceInfo
import gr.nikkpap.netsonar.net.Link
import gr.nikkpap.netsonar.net.NetInfoReader
import gr.nikkpap.netsonar.net.NetSnapshot
import gr.nikkpap.netsonar.net.NetUtil
import gr.nikkpap.netsonar.net.ScanEvent
import gr.nikkpap.netsonar.net.Scanner
import gr.nikkpap.netsonar.net.Vendor
import gr.nikkpap.netsonar.net.Wan
import gr.nikkpap.netsonar.net.WanInfo
import gr.nikkpap.netsonar.update.ReleaseInfo
import gr.nikkpap.netsonar.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Locale

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdateState
    data class Error(val message: String) : UpdateState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val appCtx: Context = app
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val scanner = Scanner()

    // ---------- settings ----------
    var lang by mutableStateOf(
        prefs.getString("lang", null) ?: if (Locale.getDefault().language == "el") "el" else "en"
    )
        private set
    var themeMode by mutableIntStateOf(prefs.getInt("theme", 0))
        private set // 0 auto, 1 dark, 2 light
    var autoUpdate by mutableStateOf(prefs.getBoolean("auto_update", true))
        private set

    fun setLanguage(l: String) { lang = l; prefs.edit().putString("lang", l).apply() }
    fun setTheme(m: Int) { themeMode = m; prefs.edit().putInt("theme", m).apply() }
    fun setAutoUpdateCheck(b: Boolean) { autoUpdate = b; prefs.edit().putBoolean("auto_update", b).apply() }
    fun shouldAskPermsOnce(): Boolean {
        if (prefs.getBoolean("asked_perms", false)) return false
        prefs.edit().putBoolean("asked_perms", true).apply()
        return true
    }

    // ---------- network info ----------
    var snapshot by mutableStateOf<NetSnapshot?>(null)
        private set
    var wan by mutableStateOf<WanInfo?>(null)
        private set
    var wanLoading by mutableStateOf(false)
        private set
    var hasLocationPerm by mutableStateOf(false)
        private set
    var locationOn by mutableStateOf(true)
        private set
    private var lastWan = 0L

    fun refreshNetwork(fetchWan: Boolean = true) {
        hasLocationPerm = checkPerms()
        locationOn = NetInfoReader.locationEnabled(appCtx)
        viewModelScope.launch {
            snapshot = withContext(Dispatchers.IO) {
                try { NetInfoReader.read(appCtx) } catch (_: Exception) { NetSnapshot(emptyList(), null) }
            }
            if (fetchWan && System.currentTimeMillis() - lastWan > 30_000 && !wanLoading) {
                lastWan = System.currentTimeMillis()
                wanLoading = true
                wan = Wan.fetch()
                wanLoading = false
            }
        }
    }

    fun forceRefresh() { lastWan = 0; refreshNetwork(true) }

    fun onPermissionsResult() { forceRefresh(); scanWifi() }

    private fun checkPerms() =
        ContextCompat.checkSelfPermission(appCtx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    // network change listener → auto refresh
    private val cm = app.getSystemService(ConnectivityManager::class.java)
    private var debounce: Job? = null
    private fun schedule(wanToo: Boolean) {
        debounce?.cancel()
        debounce = viewModelScope.launch {
            delay(700)
            if (wanToo) lastWan = 0
            refreshNetwork(wanToo)
        }
    }
    private val netCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = schedule(true)
        override fun onLost(network: Network) = schedule(true)
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = schedule(true)
    }

    // ---------- device scan ----------
    val devices = mutableStateListOf<Device>()
    var scanning by mutableStateOf(false)
        private set
    var identifying by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var hasScanned by mutableStateOf(false)
        private set
    var scanError by mutableStateOf<String?>(null)
        private set
    /** Small technical summary of the last scan, for troubleshooting. */
    var scanInfo by mutableStateOf<String?>(null)
        private set
    private var scanJob: Job? = null

    /** Live, human-readable scan log shown on the Devices screen (troubleshooting). */
    val scanLog = mutableStateListOf<String>()
    private var t0 = 0L
    private fun log(msg: String) {
        val sec = if (t0 == 0L) 0.0 else (System.currentTimeMillis() - t0) / 1000.0
        scanLog.add("%5.1fs  %s".format(Locale.US, sec, msg))
        while (scanLog.size > 60) scanLog.removeAt(0)
    }

    /** @return false when there is no Wi-Fi / LAN to scan. */
    fun toggleScan(): Boolean {
        if (scanning) { scanJob?.cancel(); scanning = false; identifying = false; log("Stopped by user"); return true }
        scanLog.clear(); t0 = System.currentTimeMillis()
        log("v${BuildConfig.VERSION_NAME} · SCAN pressed")
        val snap = try { NetInfoReader.read(appCtx) } catch (e: Exception) {
            log("Network read error: ${e.javaClass.simpleName} ${e.message}"); NetSnapshot(emptyList(), null)
        }
        snapshot = snap
        log("Networks: " + snap.ifaces.joinToString { "${it.type}/${it.name}/${it.ipv4 ?: "no-IPv4"}" }.ifEmpty { "none" })
        val targets = snap.scannable
        if (targets.isEmpty()) { log("Nothing to scan: no Wi-Fi/LAN with IPv4"); return false }
        targets.forEach { log("Target ${it.name} ${it.ipv4}/${it.prefix} gw ${it.gateway ?: "-"}") }
        startScan(targets)
        return true
    }

    private fun startScan(targets: List<IfaceInfo>) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            scanning = true; identifying = false; progress = 0f; hasScanned = true; scanError = null
            devices.clear()
            scanner.resetStats(); scanInfo = null
            val ifDesc = targets.joinToString(" + ") { "${it.name ?: "?"} ${it.ipv4}/${it.prefix} gw ${it.gateway ?: "-"}" }
            val totals = IntArray(targets.size)
            val dones = IntArray(targets.size)
            try {
                coroutineScope {
                    targets.forEachIndexed { idx, iface ->
                        launch {
                            scanner.scan(iface).collect { ev ->
                                when (ev) {
                                    is ScanEvent.Found -> {
                                        upsert(ev.device)
                                        val d = ev.device
                                        log("+ ${d.ip}" + (if (d.isSelf) " (this phone)" else "") + (if (d.isGateway) " (router)" else "") +
                                            (d.latencyMs?.let { " $it ms" } ?: "") + (if (d.openPorts.isNotEmpty()) " ports ${d.openPorts}" else ""))
                                    }
                                    is ScanEvent.Progress -> {
                                        totals[idx] = ev.total; dones[idx] = ev.done
                                        progress = dones.sum().toFloat() / totals.sum().coerceAtLeast(1)
                                    }
                                }
                            }
                        }
                    }
                }
                progress = 1f
                log("Sweep done: ${devices.size} devices · ping ${scanner.pingHits.get()} · tcp ${scanner.tcpHits.get()}" +
                    (scanner.lastError?.let { " · last error: $it" } ?: ""))

                // ARP (older Android / TV boxes) → MACs + hosts missed by the sweep
                val arp1 = scanner.readArp()
                log("Neighbour table: ${scanner.arpInfo}")
                applyArp(arp1, targets)

                // names + NetBIOS MACs
                val netFor = targets.associate { (it.link ?: Link.LAN) to it.network }
                val sem = Semaphore(12)
                coroutineScope {
                    devices.toList().filter { !it.isSelf }.forEach { d ->
                        launch {
                            sem.withPermit {
                                val e = scanner.enrich(d, netFor[d.link])
                                modify(d) { cur ->
                                    val upd = cur.copy(hostname = cur.hostname ?: e.hostname, mac = cur.mac ?: e.mac)
                                    upd.copy(kind = Scanner.guessKind(upd))
                                }
                            }
                        }
                    }
                }
                applyArp(scanner.readArp(), targets)
                log("Done: ${devices.size} devices")
                scanInfo = "$ifDesc · IPs ${totals.sum()} · ping ${scanner.pingHits.get()} · tcp ${scanner.tcpHits.get()} · ${scanner.arpInfo}" +
                    (scanner.lastError?.let { " · err: $it" } ?: "")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                scanError = e.javaClass.simpleName + ": " + (e.message ?: "")
                log("ERROR $scanError")
            } finally {
                scanning = false
            }

            // manufacturers (slow, 1/sec, cached) — UI is already usable
            identifying = true
            try {
                for (d in devices.toList()) {
                    val mac = d.mac ?: continue
                    val v = Vendor.lookup(appCtx, mac) ?: continue
                    modify(d) { cur -> cur.copy(vendor = v).let { it.copy(kind = Scanner.guessKind(it)) } }
                }
            } finally {
                identifying = false
            }
        }
    }

    private fun applyArp(arp: Map<String, String>, targets: List<IfaceInfo>) {
        if (arp.isEmpty()) return
        for ((ip, mac) in arp) {
            val i = devices.indexOfFirst { it.ip == ip }
            if (i >= 0) {
                if (devices[i].mac == null) devices[i] = devices[i].copy(mac = mac).let { it.copy(kind = Scanner.guessKind(it)) }
            } else {
                val t = targets.firstOrNull { it.ipv4 != null && NetUtil.sameSubnet(ip, it.ipv4, it.prefix) } ?: continue
                upsert(Device(ip, NetUtil.ipToLong(ip), t.link ?: Link.LAN, mac = mac, isGateway = ip == t.gateway)
                    .let { it.copy(kind = Scanner.guessKind(it)) })
            }
        }
    }

    private fun upsert(d: Device) {
        val i = devices.indexOfFirst { it.key == d.key }
        if (i >= 0) { devices[i] = d; return }
        val pos = devices.indexOfFirst { it.link.ordinal > d.link.ordinal || (it.link == d.link && it.sortKey > d.sortKey) }
        if (pos < 0) devices.add(d) else devices.add(pos, d)
    }

    private fun modify(d: Device, f: (Device) -> Device) {
        val i = devices.indexOfFirst { it.key == d.key }
        if (i >= 0) devices[i] = f(devices[i])
    }

    // ---------- Wi-Fi scan ----------
    var aps by mutableStateOf<List<AccessPoint>>(emptyList())
        private set
    var wifiScanning by mutableStateOf(false)
        private set
    var wifiEnabled by mutableStateOf(true)
        private set
    var wifiThrottled by mutableStateOf(false)
        private set
    private var receiverOn = false
    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadScanResults(); wifiScanning = false
        }
    }

    fun scanWifi() {
        val wm = appCtx.getSystemService(WifiManager::class.java) ?: return
        wifiEnabled = wm.isWifiEnabled
        if (!wifiEnabled) { aps = emptyList(); return }
        if (!receiverOn) {
            ContextCompat.registerReceiver(
                appCtx, wifiReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverOn = true
        }
        loadScanResults()
        @Suppress("DEPRECATION")
        val started = try { wm.startScan() } catch (_: Exception) { false }
        wifiThrottled = !started
        wifiScanning = started
        if (started) viewModelScope.launch { delay(9000); wifiScanning = false }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun loadScanResults() {
        val wm = appCtx.getSystemService(WifiManager::class.java) ?: return
        val results: List<ScanResult> = try { wm.scanResults ?: emptyList() } catch (_: Exception) { emptyList() }
        val cur = snapshot?.wifi?.bssid
        aps = results.mapNotNull { r ->
            val link = NetUtil.freqToLink(r.frequency) ?: return@mapNotNull null
            val width = when (r.channelWidth) { 0 -> 20; 1 -> 40; 2 -> 80; 3 -> 160; 4 -> 160; 5 -> 320; else -> 20 }
            val center = if (width > 20 && r.centerFreq0 > 0) r.centerFreq0 else r.frequency
            AccessPoint(
                ssid = r.SSID?.takeIf { it.isNotBlank() },
                bssid = (r.BSSID ?: "").uppercase(),
                level = r.level,
                freq = r.frequency,
                centerFreq = center,
                link = link,
                channel = NetUtil.freqToChannel(r.frequency),
                widthMhz = width,
                security = security(r.capabilities ?: ""),
                connected = cur != null && r.BSSID.equals(cur, ignoreCase = true),
            )
        }.sortedWith(compareByDescending<AccessPoint> { it.connected }.thenByDescending { it.level })
    }

    private fun security(c: String) = when {
        c.contains("WPA3") || c.contains("SAE") -> "WPA3"
        c.contains("WPA2") || c.contains("RSN") -> "WPA2"
        c.contains("WPA") -> "WPA"
        c.contains("WEP") -> "WEP"
        c.contains("OWE") -> "OWE"
        else -> ""
    }

    // ---------- OTA updates ----------
    var update by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set
    var showUpdateDialog by mutableStateOf(false)

    fun checkUpdates(silent: Boolean) {
        if (update is UpdateState.Checking || update is UpdateState.Downloading) return
        viewModelScope.launch {
            if (!silent) update = UpdateState.Checking
            try {
                val r = Updater.latest()
                if (r.version.isNotBlank() && Updater.isNewer(r.version, BuildConfig.VERSION_NAME)) {
                    update = UpdateState.Available(r); showUpdateDialog = true
                } else update = if (silent) UpdateState.Idle else UpdateState.UpToDate
            } catch (e: Exception) {
                update = if (silent) UpdateState.Idle else UpdateState.Error(e.message ?: "")
            }
        }
    }

    /** @return false if the user must first allow "install unknown apps". */
    fun downloadAndInstall(r: ReleaseInfo): Boolean {
        val url = r.apkUrl ?: return true
        if (!Updater.canInstall(appCtx)) { Updater.openInstallPermission(appCtx); return false }
        viewModelScope.launch {
            update = UpdateState.Downloading(r, 0f)
            try {
                val f = Updater.download(appCtx, url) { p ->
                    viewModelScope.launch(Dispatchers.Main) { update = UpdateState.Downloading(r, p) }
                }
                update = UpdateState.Available(r)
                Updater.install(appCtx, f)
            } catch (e: Exception) {
                update = UpdateState.Error(e.message ?: "")
            }
        }
        return true
    }

    // ---------- lifecycle ----------
    init {
        try {
            val req = NetworkRequest.Builder()
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build()
            cm.registerNetworkCallback(req, netCallback)
        } catch (_: Exception) { }
        refreshNetwork(true)
        if (autoUpdate) checkUpdates(silent = true)
    }

    override fun onCleared() {
        try { cm.unregisterNetworkCallback(netCallback) } catch (_: Exception) { }
        if (receiverOn) try { appCtx.unregisterReceiver(wifiReceiver) } catch (_: Exception) { }
        super.onCleared()
    }
}
