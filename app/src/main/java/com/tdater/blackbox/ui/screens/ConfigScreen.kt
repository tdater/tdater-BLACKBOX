package com.tdater.blackbox.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tdater.blackbox.core.compare.ComparisonEngine
import com.tdater.blackbox.core.model.EnvField
import com.tdater.blackbox.core.model.EnvValue
import com.tdater.blackbox.ui.MainViewModel
import com.tdater.blackbox.ui.components.BbTopBar
import com.tdater.blackbox.ui.components.Field
import com.tdater.blackbox.ui.components.InfoCard
import com.tdater.blackbox.ui.components.SectionHeader
import com.tdater.blackbox.ui.components.fmtTime
import com.tdater.blackbox.ui.components.shortId

private const val SEP = '\u001F'

/** Editable env row encoded for saved state: key, value, unit, known. */
private data class Row4(val key: String, val value: String, val unit: String, val known: Boolean) {
    fun encode() = listOf(key, value, unit, if (known) "1" else "0").joinToString(SEP.toString())
    companion object {
        fun decode(s: String) = s.split(SEP).let { Row4(it.getOrElse(0) { "" }, it.getOrElse(1) { "" }, it.getOrElse(2) { "" }, it.getOrElse(3) { "1" } == "1") }
    }
}

/**
 * Build references and environment snapshots. Snapshots are immutable: "edit" means capturing a
 * new snapshot (optionally starting from an old one), so earlier runs keep their recorded context.
 */
@Composable
fun ConfigScreen(vm: MainViewModel, pid: String, back: () -> Unit) {
    val bundle by rememberBundle(vm, pid)
    val b = bundle
    var bName by rememberSaveable { mutableStateOf("") }
    var bVersion by rememberSaveable { mutableStateOf("") }
    var bCode by rememberSaveable { mutableStateOf("") }
    var bCommit by rememberSaveable { mutableStateOf("") }
    var rows by rememberSaveable { mutableStateOf(listOf(Row4("", "", "", true).encode())) }

    fun setRow(i: Int, r: Row4) { rows = rows.toMutableList().also { it[i] = r.encode() } }

    Scaffold(topBar = { BbTopBar("Builds & environments", b?.project?.title, onBack = back) }) { pad ->
        if (b == null) return@Scaffold
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp).widthIn(max = 760.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader("Add build reference")
            Text("Enter what you know. Leave unknown fields blank; they will be shown as not recorded, never guessed.",
                style = MaterialTheme.typography.labelSmall)
            Field("Display name", bName, { bName = it }, singleLine = true, placeholder = "KITT 2.3 debug #41")
            Field("versionName", bVersion, { bVersion = it }, singleLine = true)
            Field("versionCode", bCode, { bCode = it.filter(Char::isDigit) }, singleLine = true)
            Field("Commit / tag / reference", bCommit, { bCommit = it }, singleLine = true)
            Button(enabled = bName.isNotBlank(), onClick = {
                val n = bName; val v = bVersion; val c = bCode.toLongOrNull(); val cm = bCommit
                vm.launch { vm.repo.addBuild(pid, n, v, c, cm, "user entry"); bName = ""; bVersion = ""; bCode = ""; bCommit = ""; vm.message = "Build saved." }
            }) { Text("Save build") }

            SectionHeader("Builds (${b.builds.size})")
            b.builds.sortedByDescending { it.createdAt }.forEach {
                InfoCard(Modifier.padding(vertical = 2.dp)) {
                    Text(it.displayName, style = MaterialTheme.typography.titleSmall)
                    Text("versionName ${it.versionName ?: "—"} · versionCode ${it.versionCode ?: "—"} · ref ${it.commitRef ?: "—"} · ${it.provenance} · ${shortId(it.id)}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            SectionHeader("Capture environment snapshot")
            Text("Tick \"unknown\" when you could not check a value. Unknown is different from blank or unchanged.",
                style = MaterialTheme.typography.labelSmall)
            Row {
                OutlinedButton(onClick = {
                    val auto = listOf(
                        Row4("Device", "${Build.MANUFACTURER} ${Build.MODEL}", "", true),
                        Row4("Device codename", Build.DEVICE, "", true),
                        Row4("Android", "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})", "", true),
                        Row4("Security patch", Build.VERSION.SECURITY_PATCH, "", true),
                    )
                    rows = (auto + rows.map { Row4.decode(it) }.filter { it.key.isNotBlank() }).distinctBy { it.key }.map { it.encode() }
                }, modifier = Modifier.padding(end = 8.dp)) { Text("Fill from this phone") }
                b.environments.maxByOrNull { it.capturedAt }?.let { last ->
                    OutlinedButton(onClick = {
                        rows = last.fields.map { f ->
                            when (val v = f.value) {
                                is EnvValue.Known -> Row4(f.key, v.text, v.unit ?: "", true)
                                is EnvValue.Unknown -> Row4(f.key, v.note, "", false)
                            }.encode()
                        }
                    }) { Text("Start from latest") }
                }
            }
            rows.map { Row4.decode(it) }.forEachIndexed { i, r ->
                InfoCard(Modifier.padding(vertical = 2.dp)) {
                    Field("Key", r.key, { setRow(i, r.copy(key = it)) }, singleLine = true, placeholder = "Adapter / Connection / Battery mode…")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Field(if (r.known) "Value" else "Note (why unknown)", r.value, { setRow(i, r.copy(value = it)) }, Modifier.weight(2f), singleLine = true)
                        if (r.known) Field("Unit", r.unit, { setRow(i, r.copy(unit = it)) }, Modifier.weight(1f).padding(start = 6.dp), singleLine = true)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = !r.known, onCheckedChange = { setRow(i, r.copy(known = !it)) })
                        Text("Unknown", modifier = Modifier.weight(1f))
                        TextButton(onClick = { rows = rows.toMutableList().also { it.removeAt(i) } }) { Text("Remove") }
                    }
                }
            }
            Row {
                OutlinedButton(onClick = { rows = rows + Row4("", "", "", true).encode() }, modifier = Modifier.padding(end = 8.dp)) { Text("+ Field") }
                Button(enabled = rows.map { Row4.decode(it) }.any { it.key.isNotBlank() }, onClick = {
                    val fields = rows.map { Row4.decode(it) }.filter { it.key.isNotBlank() }.map {
                        EnvField(it.key.trim(), if (it.known) EnvValue.Known(it.value.trim(), it.unit.trim().ifEmpty { null }) else EnvValue.Unknown(it.value.trim()))
                    }
                    vm.launch { vm.repo.captureEnvironment(pid, "user entry", fields); vm.message = "Snapshot saved (${fields.size} fields)." }
                }) { Text("Save snapshot") }
            }

            SectionHeader("Snapshots (${b.environments.size})")
            b.environments.sortedByDescending { it.capturedAt }.forEach { e ->
                InfoCard(Modifier.padding(vertical = 2.dp)) {
                    Text("${fmtTime(e.capturedAt)} · ${e.source} · ${shortId(e.id)}", style = MaterialTheme.typography.labelMedium)
                    e.fields.forEach { Text("${it.key}: ${ComparisonEngine.render(it.value)}", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}
