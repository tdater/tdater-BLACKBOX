package com.tdater.blackbox.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.tdater.blackbox.core.model.EvidenceKind
import com.tdater.blackbox.system.NetTools
import com.tdater.blackbox.ui.MainViewModel
import com.tdater.blackbox.ui.components.BbTopBar
import com.tdater.blackbox.ui.components.Field
import com.tdater.blackbox.ui.components.InfoCard
import com.tdater.blackbox.ui.components.SectionHeader

/** BLACKBOX Net only: ping, port check and DNS lookup, with the output saved as a log record. */
@Composable
fun NetToolsScreen(vm: MainViewModel, pid: String, iid: String?, back: () -> Unit) {
    val bundle by rememberBundle(vm, pid)
    var tool by rememberSaveable { mutableStateOf("ping") }
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("443") }
    var output by rememberSaveable { mutableStateOf("") }
    var running by rememberSaveable { mutableStateOf(false) }
    val inv = bundle?.investigations?.firstOrNull { it.id == iid }

    Scaffold(topBar = { BbTopBar("Network tools", bundle?.project?.title, onBack = back) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp).widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoCard {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ping" to "Ping", "port" to "Port check", "dns" to "DNS lookup").forEach { (id, label) ->
                        FilterChip(selected = tool == id, onClick = { tool = id }, label = { Text(label) })
                    }
                }
                Field("Host or IP", host, { host = it }, singleLine = true, placeholder = "192.168.1.1 or example.com")
                if (tool == "port") Field("Port", port, { port = it.filter(Char::isDigit).take(5) }, singleLine = true, placeholder = "443")
                Button(enabled = host.isNotBlank() && !running, onClick = {
                    running = true
                    vm.launch("Network tool failed") {
                        try {
                            output = when (tool) {
                                "port" -> NetTools.port(host, port.toIntOrNull() ?: 0)
                                "dns" -> NetTools.dns(host)
                                else -> NetTools.ping(host)
                            }
                        } finally { running = false }
                    }
                }) { Text(if (running) "Running…" else "Run") }
                Text("These tools contact the host you type, from this phone. Nothing else in BLACKBOX uses the network.",
                    style = MaterialTheme.typography.labelSmall)
            }
            if (output.isNotBlank()) {
                SectionHeader("Result")
                InfoCard {
                    SelectionContainer { Text(output, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                    OutlinedButton(onClick = {
                        val text = output
                        val title = text.lineSequence().first().substringBefore(" (")
                        vm.launch("Save failed") {
                            vm.repo.addTextEvidence(pid, iid, null, EvidenceKind.LOG, title, text)
                            vm.message = "Saved to ${inv?.title ?: bundle?.project?.title ?: "project"}."
                        }
                    }) { Text("Save as a record") }
                }
            }
        }
    }
}
