package gr.nikkpap.netsonar.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import gr.nikkpap.netsonar.MainViewModel
import gr.nikkpap.netsonar.i18n.LocalStrings

@Composable
fun NetSonarApp(vm: MainViewModel, requestPerms: () -> Unit) {
    val s = LocalStrings.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        Icons.Rounded.Router to s.tabNetwork,
        Icons.Rounded.WifiTethering to s.tabDevices,
        Icons.Rounded.Wifi to s.tabWifi,
        Icons.Rounded.Tune to s.tabMore,
    )
    Box(Modifier.fillMaxSize()) {
        AppBackground()
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
                    tabs.forEachIndexed { i, (icon, label) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(icon, label) },
                            label = { Text(label, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Accent.ink,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad)) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 24 }) togetherWith fadeOut(tween(140))
                    },
                    label = "tabs",
                ) { t ->
                    when (t) {
                        0 -> NetworkScreen(vm, requestPerms)
                        1 -> DevicesScreen(vm)
                        2 -> WifiScreen(vm, requestPerms)
                        else -> MoreScreen(vm)
                    }
                }
            }
        }
        UpdateDialog(vm)
    }
}
