package gr.nikkpap.netsonar

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import gr.nikkpap.netsonar.i18n.EN
import gr.nikkpap.netsonar.i18n.GR
import gr.nikkpap.netsonar.i18n.LocalStrings
import gr.nikkpap.netsonar.ui.NetSonarApp
import gr.nikkpap.netsonar.ui.NetSonarTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onPermissionsResult()
    }

    private fun requestPerms() {
        val p = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.NEARBY_WIFI_DEVICES
        permLauncher.launch(p.toTypedArray())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!vm.hasLocationPerm && vm.shouldAskPermsOnce()) requestPerms()
        setContent {
            val dark = when (vm.themeMode) { 1 -> true; 2 -> false; else -> isSystemInDarkTheme() }
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            CompositionLocalProvider(LocalStrings provides if (vm.lang == "el") GR else EN) {
                NetSonarTheme(dark) {
                    NetSonarApp(vm) { requestPerms() }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshNetwork(true)
    }
}
