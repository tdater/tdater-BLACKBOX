package com.tdater.blackbox.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tdater.blackbox.core.model.ResumeCapsule
import com.tdater.blackbox.core.resume.Freshness
import com.tdater.blackbox.core.resume.ResumeGenerator
import com.tdater.blackbox.ui.MainViewModel
import com.tdater.blackbox.ui.components.BbTopBar
import com.tdater.blackbox.ui.components.Field
import com.tdater.blackbox.ui.components.InfoCard
import com.tdater.blackbox.ui.components.LabeledValue
import com.tdater.blackbox.ui.components.SectionHeader
import com.tdater.blackbox.ui.components.Tag
import com.tdater.blackbox.ui.components.Tone
import com.tdater.blackbox.ui.components.fmtFull
import com.tdater.blackbox.ui.components.fmtTime
import com.tdater.blackbox.ui.components.shortId

/**
 * Resume capsules are insert-only: refreshing or editing creates a new capsule, so an approved
 * historical capsule is never silently rewritten. Staleness comes from the investigation revision.
 */
@Composable
fun ResumeScreen(vm: MainViewModel, pid: String, iid: String, back: () -> Unit, openInvestigation: (String) -> Unit) {
    val bundle by rememberBundle(vm, pid)
    var editing by rememberSaveable { mutableStateOf(false) }
    val b = bundle
    val inv = b?.investigations?.firstOrNull { it.id == iid }

    Scaffold(topBar = { BbTopBar("Resume", inv?.title, onBack = back) }) { pad ->
        if (b == null) return@Scaffold
        if (inv == null) { Column(Modifier.padding(pad)) { Missing("Investigation") }; return@Scaffold }
        val history = b.capsules.filter { it.investigationId == iid }.sortedByDescending { it.generatedAt }
        val latest = history.firstOrNull()
        val shown = latest ?: ResumeGenerator.generate(b, iid, "preview", System.currentTimeMillis())
        val fresh = latest?.let { ResumeGenerator.freshness(it, inv) }

        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp).widthIn(max = 760.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when (fresh) {
                    null -> Tag("UNSAVED PREVIEW", Tone.INFO)
                    Freshness.CURRENT -> Tag("CURRENT", Tone.GOOD)
                    Freshness.STALE -> Tag("STALE — records changed since this was saved", Tone.WARN)
                }
                Tag(shown.generationMode.name)
                if (shown.userEdited) Tag("USER-EDITED")
            }
            Text(if (latest != null) "Saved ${fmtFull(latest.generatedAt)} from revision ${latest.sourceRevision} (now ${inv.revision})"
                 else "Generated now from your records; not yet saved.", style = MaterialTheme.typography.labelMedium)
            CapsuleBody(shown)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = {
                    vm.launch { vm.repo.saveCapsule(ResumeGenerator.generate(b, iid, vm.repo.newId(), System.currentTimeMillis())); vm.message = "New capsule saved; older ones are kept." }
                }) { Text(if (latest == null) "Save capsule" else "Refresh (save new)") }
                OutlinedButton(onClick = { editing = true }) { Text("Edit") }
                OutlinedButton(onClick = { openInvestigation(iid) }) { Text("Open investigation") }
            }
            if (history.size > 1) {
                SectionHeader("Earlier capsules (${history.size - 1})")
                history.drop(1).forEach { c ->
                    InfoCard(Modifier.padding(vertical = 3.dp)) {
                        Text("${fmtTime(c.generatedAt)} · rev ${c.sourceRevision}" + if (c.userEdited) " · edited" else "", style = MaterialTheme.typography.labelMedium)
                        Text("Next: ${c.nextAction}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (editing) EditCapsuleDialog(shown, onDismiss = { editing = false }) { edited ->
            editing = false
            vm.launch {
                vm.repo.saveCapsule(edited.copy(id = vm.repo.newId(), generatedAt = System.currentTimeMillis(),
                    sourceRevision = inv.revision, userEdited = true))
            }
        }
    }
}

@Composable
private fun CapsuleBody(c: ResumeCapsule) {
    SectionHeader("Objective"); Text(c.objective, style = MaterialTheme.typography.bodyLarge)
    SectionHeader("Known-good state"); Text(c.knownGoodState)
    SectionHeader("Current failure"); Text(c.currentFailure)
    SectionHeader("Last tested change"); Text(c.lastTestedChange)
    SectionHeader("Next action"); Text(c.nextAction, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    SectionHeader("Sources")
    if (c.references.isEmpty()) Text("No source records.")
    c.references.forEach { LabeledValue(it.kind, "${it.label} [${shortId(it.id)}]") }
}

@Composable
private fun EditCapsuleDialog(c: ResumeCapsule, onDismiss: () -> Unit, onSave: (ResumeCapsule) -> Unit) {
    var objective by rememberSaveable { mutableStateOf(c.objective) }
    var good by rememberSaveable { mutableStateOf(c.knownGoodState) }
    var failure by rememberSaveable { mutableStateOf(c.currentFailure) }
    var change by rememberSaveable { mutableStateOf(c.lastTestedChange) }
    var next by rememberSaveable { mutableStateOf(c.nextAction) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit capsule (saved as a new version)") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Field("Objective", objective, { objective = it })
                Field("Known-good state", good, { good = it })
                Field("Current failure", failure, { failure = it })
                Field("Last tested change", change, { change = it })
                Field("Next action", next, { next = it })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(c.copy(objective = objective, knownGoodState = good, currentFailure = failure, lastTestedChange = change, nextAction = next)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
