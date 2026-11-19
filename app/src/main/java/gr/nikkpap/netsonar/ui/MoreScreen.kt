package gr.nikkpap.netsonar.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.GetApp
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gr.nikkpap.netsonar.BuildConfig
import gr.nikkpap.netsonar.MainViewModel
import gr.nikkpap.netsonar.UpdateState
import gr.nikkpap.netsonar.i18n.LocalStrings
import gr.nikkpap.netsonar.update.Updater

@Composable
fun MoreScreen(vm: MainViewModel) {
    val s = LocalStrings.current
    val uri = LocalUriHandler.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(s.tabMore, s.settings) }

        item {
            GlassCard {
                SectionLabel(s.language, Icons.Rounded.Translate, Accent.cyan)
                Spacer(Modifier.size(10.dp))
                ChoiceRow(listOf("en" to "🇬🇧  English", "el" to "🇬🇷  Ελληνικά"), vm.lang) { vm.setLanguage(it) }
                Spacer(Modifier.size(16.dp))
                SectionLabel(s.theme, Icons.Rounded.Palette, Accent.violet)
                Spacer(Modifier.size(10.dp))
                ChoiceRow(
                    listOf("0" to s.themeSystem, "1" to s.themeDark, "2" to s.themeLight),
                    vm.themeMode.toString(),
                ) { vm.setTheme(it.toInt()) }
            }
        }

        item {
            GlassCard {
                SectionLabel(s.updates, Icons.Rounded.SystemUpdate, Accent.green)
                Spacer(Modifier.size(10.dp))
                Text("${s.version} ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.size(8.dp))
                when (val u = vm.update) {
                    UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp)); Text(s.checking)
                    }
                    UpdateState.UpToDate -> Text(s.upToDate, color = Accent.green, fontWeight = FontWeight.SemiBold)
                    is UpdateState.Error -> Text("${s.updateError} (${u.message})", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    is UpdateState.Available -> Text(s.updateAvailable(u.release.version), color = Accent.amber, fontWeight = FontWeight.Bold)
                    is UpdateState.Downloading -> LinearProgressIndicator(progress = { u.progress }, modifier = Modifier.fillMaxWidth())
                    UpdateState.Idle -> {}
                }
                Spacer(Modifier.size(10.dp))
                Button(
                    onClick = {
                        val u = vm.update
                        if (u is UpdateState.Available) vm.showUpdateDialog = true else vm.checkUpdates(silent = false)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent.green, contentColor = Accent.ink),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (vm.update is UpdateState.Available) s.downloadInstall else s.checkUpdates, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.size(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.autoCheck, modifier = Modifier.weight(1f), fontSize = 14.sp)
                    Switch(checked = vm.autoUpdate, onCheckedChange = { vm.setAutoUpdateCheck(it) })
                }
            }
        }

        item {
            GlassCard {
                SectionLabel(s.howTitle, Icons.Rounded.Info, Accent.amber)
                Spacer(Modifier.size(8.dp))
                Text(s.howText, fontSize = 14.sp, lineHeight = 21.sp)
            }
        }

        item {
            GlassCard(borderBrush = Brush.linearGradient(listOf(Accent.cyan, Accent.violet, Accent.pink))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(58.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Accent.cyan, Accent.violet))),
                        contentAlignment = Alignment.Center,
                    ) { Text("NS", fontWeight = FontWeight.Black, fontSize = 22.sp, color = Accent.ink) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        GradientTitle("NetSonar", 24)
                        Text("${s.version} ${BuildConfig.VERSION_NAME}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.size(12.dp))
                Text(s.developer, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Nikolaos K. Paridis (aka nikkpap)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("ALU DEV TEAM", fontSize = 13.sp, color = Accent.cyan, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.size(12.dp))
                OutlinedButton(onClick = { try { uri.openUri(Updater.REPO_URL) } catch (_: Exception) { } }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Code, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(s.sourceCode)
                }
            }
        }
    }
}

@Composable
fun UpdateDialog(vm: MainViewModel) {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val st = vm.update
    if (!vm.showUpdateDialog) return
    val rel = when (st) {
        is UpdateState.Available -> st.release
        is UpdateState.Downloading -> st.release
        else -> return
    }
    val downloading = st is UpdateState.Downloading
    AlertDialog(
        onDismissRequest = { if (!downloading) vm.showUpdateDialog = false },
        icon = { Icon(Icons.Rounded.GetApp, null, tint = Accent.green) },
        title = { Text(s.updateAvailable(rel.version), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (rel.notes.isNotBlank()) {
                    Text(s.whatsNew, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.size(6.dp))
                    Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                        Text(rel.notes, fontSize = 13.sp)
                    }
                }
                if (st is UpdateState.Downloading) {
                    Spacer(Modifier.size(12.dp))
                    Text("${s.downloading} ${(st.progress * 100).toInt()}%", fontSize = 13.sp)
                    LinearProgressIndicator(progress = { st.progress }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !downloading,
                onClick = {
                    if (rel.apkUrl == null) {
                        try { uri.openUri(rel.pageUrl) } catch (_: Exception) { }
                    } else if (!vm.downloadAndInstall(rel)) {
                        Toast.makeText(ctx, s.allowInstall, Toast.LENGTH_LONG).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent.green, contentColor = Accent.ink),
            ) { Text(s.downloadInstall, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            if (!downloading) TextButton(onClick = { vm.showUpdateDialog = false }) { Text(s.later) }
        },
    )
}
