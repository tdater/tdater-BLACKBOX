package com.tdater.blackbox.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tdater.blackbox.ai.ModelStore
import com.tdater.blackbox.core.ai.AiContext
import com.tdater.blackbox.core.ai.AiPrompt
import com.tdater.blackbox.core.ai.AiResult
import com.tdater.blackbox.core.model.ClaimClassification
import com.tdater.blackbox.core.model.PrivacyPolicy
import com.tdater.blackbox.ui.MainViewModel
import com.tdater.blackbox.ui.components.BbTopBar
import com.tdater.blackbox.ui.components.InfoCard
import com.tdater.blackbox.ui.components.SectionHeader
import com.tdater.blackbox.ui.components.Tag
import com.tdater.blackbox.ui.components.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val CONTEXT_LIMIT = 60_000

/**
 * Ask the imported on-device model about one investigation. The user picks a task and ticks exactly which
 * records it sees. Answers are unverified suggestions; saving one creates an INFERRED claim authored by "ai".
 */
@Composable
fun AiScreen(vm: MainViewModel, pid: String, iid: String, back: () -> Unit, openSettings: () -> Unit) {
    val bundle by rememberBundle(vm, pid)
    var model by remember { mutableStateOf<ModelStore.Info?>(null) }
    LaunchedEffect(Unit) { model = withContext(Dispatchers.IO) { vm.models.current() } }
    var task by rememberSaveable { mutableStateOf(AiPrompt.Task.NEXT_TESTS) }
    var deselected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var saved by rememberSaveable(vm.aiOutcome) { mutableStateOf(listOf<Int>()) }
    var confirmAllow by remember { mutableStateOf(false) }

    val b = bundle
    val inv = b?.investigations?.firstOrNull { it.id == iid }
    Scaffold(topBar = { BbTopBar("Ask on-device AI", inv?.title, onBack = back) }) { pad ->
        if (b == null || inv == null) {
            Text("Loading…", Modifier.padding(pad).padding(16.dp))
            return@Scaffold
        }
        val candidates = remember(b, iid) { AiContext.candidates(b, iid) }
        val selected = candidates.filter { it.item.recordId !in deselected }
        val chars = selected.sumOf { minOf(it.item.text.length, AiPrompt.ITEM_CHAR_LIMIT) }
        val aiOn = vm.settings.aiEnabled
        val allowed = b.project.privacyPolicy == PrivacyPolicy.AI_ALLOWED
        val running = vm.aiRunningFor != null
        val ready = aiOn && allowed && model != null && selected.isNotEmpty() && chars <= CONTEXT_LIMIT && !running

        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp).widthIn(max = 840.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoCard {
                Text("Runs a model file on this phone. Nothing leaves the device. Answers are guesses to check, not findings.",
                    style = MaterialTheme.typography.bodySmall)
                GateLine(aiOn, "AI switch is on", "AI is off in Settings", "Open Settings", openSettings)
                GateLine(allowed, "Project is AI ALLOWED", "Project is LOCAL ONLY, so no AI may read it", "Allow AI for this project") { confirmAllow = true }
                GateLine(model != null, "Model: ${model?.displayName.orEmpty()}", "No model imported", "Import in Settings", openSettings)
            }

            SectionHeader("Task")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiPrompt.Task.entries.forEach { t -> FilterChip(selected = task == t, onClick = { task = t }, label = { Text(t.label) }) }
            }

            SectionHeader("Records the model will see (${selected.size}/${candidates.size}, $chars of $CONTEXT_LIMIT characters)")
            if (candidates.isEmpty()) Text("This investigation has no records yet.")
            candidates.forEach { c ->
                val on = c.item.recordId !in deselected
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = on, onCheckedChange = {
                        deselected = if (it) deselected - c.item.recordId else deselected + c.item.recordId
                    }, modifier = Modifier.semantics { contentDescription = "Include ${c.label}" })
                    Text(c.label, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                }
            }
            if (chars > CONTEXT_LIMIT) Tag("Too much context: untick some records", Tone.WARN)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(enabled = ready, onClick = {
                    vm.askAi(b.project, inv.title, iid, task, selected.map { it.item })
                }) { Text("Ask: ${task.label}") }
                if (running) OutlinedButton(onClick = { vm.cancelAi() }) { Text("Cancel") }
            }
            if (running) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Generating on the phone's CPU. Small models take tens of seconds; big ones take minutes. You can leave this screen.",
                    style = MaterialTheme.typography.bodySmall)
            }

            val outcome = vm.aiOutcome?.takeIf { it.investigationId == iid }
            if (outcome != null) {
                SectionHeader("Answer: ${outcome.task.label}")
                when (val r = outcome.result) {
                    is AiResult.Ok -> {
                        Tag("UNVERIFIED · ${r.provider}", Tone.WARN)
                        if (r.suggestions.isEmpty()) Text("The model gave no usable bullet points. Try fewer records or another task.")
                        val labels = candidates.associate { it.item.recordId to it.label }
                        r.suggestions.forEachIndexed { i, s ->
                            InfoCard {
                                Text(s.text)
                                Text(if (s.referencedIds.isEmpty()) "Cites no records."
                                    else "Cites: " + s.referencedIds.joinToString("; ") { labels[it] ?: it },
                                    style = MaterialTheme.typography.bodySmall)
                                if (i in saved) Tag("Saved as INFERRED claim", Tone.GOOD)
                                else TextButton(onClick = {
                                    saved = saved + i
                                    val ev = s.referencedIds.filter { id -> b.evidence.any { it.id == id } }
                                    val runs = s.referencedIds.filter { id -> b.runs.any { it.id == id } }
                                    vm.launch("Saving claim failed") {
                                        vm.repo.addClaim(pid, iid, s.text, ClaimClassification.INFERRED, ev, runs, author = "ai")
                                    }
                                }) { Text("Save as INFERRED claim") }
                            }
                        }
                    }
                    is AiResult.Denied -> { Tag("NOT SENT", Tone.BAD); Text(r.reason) }
                    is AiResult.Unavailable -> { Tag("NO MODEL", Tone.BAD); Text(r.reason) }
                    is AiResult.Failed -> { Tag("FAILED", Tone.BAD); Text(r.reason) }
                }
            }
        }

        if (confirmAllow) AlertDialog(
            onDismissRequest = { confirmAllow = false },
            title = { Text("Allow AI for \"${b.project.title}\"?") },
            text = { Text("The project becomes AI ALLOWED. Records you tick can then be read by the on-device model. " +
                "If a cloud provider is ever added, this setting would also let it receive ticked records, so switch back to LOCAL ONLY in project settings if that matters.") },
            confirmButton = { TextButton(onClick = {
                confirmAllow = false
                vm.launch("Policy change failed") { vm.repo.updateProject(b.project.copy(privacyPolicy = PrivacyPolicy.AI_ALLOWED)) }
            }) { Text("Allow AI") } },
            dismissButton = { TextButton(onClick = { confirmAllow = false }) { Text("Keep LOCAL ONLY") } },
        )
    }
}

@Composable
private fun GateLine(ok: Boolean, okText: String, badText: String, action: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tag(if (ok) "OK" else "BLOCKED", if (ok) Tone.GOOD else Tone.BAD)
        Text(if (ok) okText else badText, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (!ok) TextButton(onClick = onAction) { Text(action) }
    }
}
