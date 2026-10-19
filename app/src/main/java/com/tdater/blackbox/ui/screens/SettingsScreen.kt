package com.tdater.blackbox.ui.screens

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tdater.blackbox.BuildConfig
import com.tdater.blackbox.ui.MainViewModel
import com.tdater.blackbox.ui.components.BbTopBar
import com.tdater.blackbox.ui.components.InfoCard
import com.tdater.blackbox.ui.components.SectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(vm: MainViewModel, back: () -> Unit, openHelp: () -> Unit) {
    val ctx = LocalContext.current
    var ai by remember { mutableStateOf(vm.settings.aiEnabled) }
    var used by remember { mutableLongStateOf(-1L) }
    var free by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { used = vm.repo.attachments.usedBytes(); free = vm.repo.attachments.freeBytes() }
    }
    fun mb(b: Long) = if (b < 0) "…" else "%.1f MB".format(b / 1048576.0)

    Scaffold(topBar = { BbTopBar("Settings", null, onBack = back) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp).widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("AI")
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Allow AI features globally", modifier = Modifier.weight(1f))
                    Switch(checked = ai, onCheckedChange = { ai = it; vm.settings.aiEnabled = it })
                }
                Text("No AI provider is configured in this build, so nothing is ever sent anywhere. Every workflow works offline. " +
                    "When a provider is added, it will only receive records you select, only from projects marked AI ALLOWED, and only while this switch is on.",
                    style = MaterialTheme.typography.bodySmall)
            }
            SectionHeader("Privacy & storage")
            InfoCard {
                Text("Attachments stored: ${mb(used)} · free on device: ${mb(free)}")
                Text("Projects default to LOCAL ONLY. Android cloud backup and device-to-device transfer are disabled for BLACKBOX data, so records live only on this phone and in archives you export yourself.",
                    style = MaterialTheme.typography.bodySmall)
                Text("LOCAL ONLY is an upload policy, not forensic protection of the device. No analytics, ads or telemetry are included.",
                    style = MaterialTheme.typography.bodySmall)
                Text("Back up a project with ⋮ → Export project archive. Import from the Projects screen; imports always become a new project.",
                    style = MaterialTheme.typography.bodySmall)
            }
            SectionHeader("Help")
            OutlinedButton(onClick = openHelp) { Text("Guide, examples and FAQ") }
            SectionHeader("Diagnostics")
            OutlinedButton(onClick = {
                vm.launch {
                    val projects = vm.repo.allProjects()
                    val report = buildString {
                        appendLine("BLACKBOX diagnostics (no project content included)")
                        appendLine("App ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}")
                        appendLine("Device ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), Android ${Build.VERSION.RELEASE} SDK ${Build.VERSION.SDK_INT}")
                        appendLine("Projects: ${projects.size} (${projects.count { it.isSample }} sample)")
                        appendLine("Attachment storage: ${mb(used)}, free ${mb(free)}")
                        appendLine("AI switch: ${if (vm.settings.aiEnabled) "on" else "off"}; provider: none configured")
                    }
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), "Share diagnostics"))
                }
            }) { Text("Share diagnostics (no project content)") }
            SectionHeader("About")
            Text("BLACKBOX ${BuildConfig.VERSION_NAME} — your projects remember what happened, what changed, and what to do next.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
