package com.tdater.blackbox.ui.screens

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.tdater.blackbox.core.templates.ProjectTemplates
import com.tdater.blackbox.system.PhoneReadings
import com.tdater.blackbox.ui.components.LocalLingo
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
    var seeded by rememberSaveable { mutableStateOf(false) }
    var pickReadings by rememberSaveable { mutableStateOf(false) }
    var editFields by rememberSaveable { mutableStateOf(false) }
    val lingo = LocalLingo.current
    val ctx = LocalContext.current

    fun setRow(i: Int, r: Row4) { rows = rows.toMutableList().also { it[i] = r.encode() } }
    fun merge(fields: List<EnvField>) {
        val incoming = fields.map { f ->
            when (val v = f.value) {
                is EnvValue.Known -> Row4(f.key, v.text, v.unit ?: "", true)
                is EnvValue.Unknown -> Row4(f.key, v.note, "", false)
            }
        }
        val existing = rows.map { Row4.decode(it) }.filter { it.key.isNotBlank() }
        val keys = incoming.map { it.key.lowercase() }.toSet()
        rows = (existing.map { e -> incoming.firstOrNull { it.key.equals(e.key, true) } ?: e } +
            incoming.filter { i -> existing.none { it.key.equals(i.key, true) } }).map { it.encode() }
        if (keys.isEmpty()) return
    }

    LaunchedEffect(b?.project?.id) {
        val p = b?.project ?: return@LaunchedEffect
        if (!seeded && p.fieldKeys.isNotEmpty() && rows.map { Row4.decode(it) }.all { it.key.isBlank() }) {
            rows = p.fieldKeys.map { Row4(it, "", "", true).encode() }
        }
        seeded = true
    }

    var pendingGroups by rememberSaveable { mutableStateOf(listOf<String>()) }
    val askPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val groups = pendingGroups.map { PhoneReadings.Group.valueOf(it) }.toSet()
        vm.launch("Phone readings failed") { merge(PhoneReadings.read(ctx, groups)); vm.message = "Phone readings added. Check them, then save the snapshot." }
    }

    Scaffold(topBar = { BbTopBar(lingo.buildsAndEnv, b?.project?.title, onBack = back) }) { pad ->
        if (b == null) return@Scaffold
        val template = ProjectTemplates.byId(b.project.templateId)
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp).widthIn(max = 760.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader("What this project tracks") { TextButton(onClick = { editFields = true }) { Text("Edit") } }
            Text(b.project.fieldKeys.joinToString(" · ").ifBlank { "No fields yet. Tap Edit to list what you want to record each time (e.g. Mileage, Weather, Firmware)." },
                style = MaterialTheme.typography.bodyMedium)
            if (template.tracksBuilds || b.builds.isNotEmpty()) {
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

            }

            SectionHeader("Capture ${lingo.snapshot.lowercase()}")
            Text("Record what was true right now. Tick \"unknown\" when you could not check a value. Unknown is different from blank or unchanged.",
                style = MaterialTheme.typography.labelSmall)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                OutlinedButton(onClick = { pickReadings = true }, modifier = Modifier.padding(end = 8.dp)) { Text("Add phone readings…") }
                if (b.project.fieldKeys.isNotEmpty()) OutlinedButton(onClick = {
                    merge(b.project.fieldKeys.filter { k -> rows.map { Row4.decode(it) }.none { it.key.equals(k, true) } }
                        .map { EnvField(it, EnvValue.Known("")) })
                }, modifier = Modifier.padding(end = 8.dp)) { Text("Add my fields") }
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
            if (b.environments.isEmpty()) Text("None yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            b.environments.sortedByDescending { it.capturedAt }.forEach { e ->
                InfoCard(Modifier.padding(vertical = 2.dp)) {
                    Text("${fmtTime(e.capturedAt)} · ${e.source} · ${shortId(e.id)}", style = MaterialTheme.typography.labelMedium)
                    e.fields.forEach { Text("${it.key}: ${ComparisonEngine.render(it.value)}", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }

    if (pickReadings) PhoneReadingsDialog(onDismiss = { pickReadings = false }) { groups ->
        pickReadings = false
        pendingGroups = groups.map { it.name }
        val perms = groups.flatMap { it.permissions }.filter {
            ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
        }
        if (perms.isEmpty()) vm.launch("Phone readings failed") {
            merge(PhoneReadings.read(ctx, groups)); vm.message = "Phone readings added. Check them, then save the snapshot."
        } else askPermissions.launch(perms.toTypedArray())
    }
    if (editFields && b != null) FieldsDialog(b.project.fieldKeys, onDismiss = { editFields = false }) { keys ->
        editFields = false
        vm.launch { vm.repo.setProjectFields(pid, keys) }
    }
}

@Composable
private fun PhoneReadingsDialog(onDismiss: () -> Unit, onRead: (Set<PhoneReadings.Group>) -> Unit) {
    var picked by rememberSaveable { mutableStateOf(listOf(PhoneReadings.Group.DEVICE.name, PhoneReadings.Group.BATTERY.name, PhoneReadings.Group.NETWORK.name)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add phone readings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("BLACKBOX asks Android for each one you tick. Nothing is sent anywhere; you review the values before saving.",
                    style = MaterialTheme.typography.bodySmall)
                PhoneReadings.Group.entries.forEach { g ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                        picked = if (g.name in picked) picked - g.name else picked + g.name
                    }) {
                        Checkbox(checked = g.name in picked, onCheckedChange = { picked = if (it) picked + g.name else picked - g.name })
                        Column {
                            Text(g.label, style = MaterialTheme.typography.bodyMedium)
                            Text(g.detail + if (g.permissions.isNotEmpty()) " · asks permission" else "", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = picked.isNotEmpty(), onClick = { onRead(picked.map { PhoneReadings.Group.valueOf(it) }.toSet()) }) { Text("Read now") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FieldsDialog(initial: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.joinToString("\n")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fields to track") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("One per line. These become the starting rows of every new snapshot.", style = MaterialTheme.typography.bodySmall)
                Field("Fields", text, { text = it }, minLines = 6)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.lines()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
