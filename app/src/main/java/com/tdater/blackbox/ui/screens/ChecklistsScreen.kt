package com.tdater.blackbox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tdater.blackbox.core.model.InvestigationStatus
import com.tdater.blackbox.core.model.Procedure
import com.tdater.blackbox.core.templates.ProjectTemplates
import com.tdater.blackbox.ui.MainViewModel
import com.tdater.blackbox.ui.components.BbTopBar
import com.tdater.blackbox.ui.components.ConfirmDialog
import com.tdater.blackbox.ui.components.EmptyState
import com.tdater.blackbox.ui.components.Field
import com.tdater.blackbox.ui.components.InfoCard
import com.tdater.blackbox.ui.components.LocalLingo
import com.tdater.blackbox.ui.components.Tag
import com.tdater.blackbox.ui.components.Tone
import com.tdater.blackbox.ui.components.fmtTime

/** Saved checklists (SOPs). Using one copies it into a problem as a fresh test, so every use is recorded on its own. */
@Composable
fun ChecklistsScreen(vm: MainViewModel, back: () -> Unit, openInvestigation: (String, String) -> Unit) {
    val procs by remember { vm.repo.observeProcedures() }.collectAsStateWithLifecycle(initialValue = null)
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var using by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { BbTopBar("Checklists", "Steps that worked, ready to reuse", onBack = back) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("New checklist") })
        },
    ) { pad ->
        val list = procs ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState("No checklists yet",
                "When a test works, open it and tap \"Save as checklist\". Or write one now: oil change, router reset, sourdough schedule, anything you repeat.",
                Modifier.padding(pad)) { Button(onClick = { creating = true }) { Text("Write a checklist") } }
            return@Scaffold
        }
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp).widthIn(max = 840.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { p ->
                val t = ProjectTemplates.byId(p.templateId)
                InfoCard(Modifier.clickable { expanded = if (expanded == p.id) null else p.id }, accent = Color(t.accent)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(t.emoji, Color(t.accent), 36.dp)
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(p.title, style = MaterialTheme.typography.titleMedium)
                            Text("${p.steps.size} step(s) · updated ${fmtTime(p.updatedAt)}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (expanded == p.id) {
                        if (p.description.isNotBlank()) Text(p.description, style = MaterialTheme.typography.bodySmall)
                        p.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodyMedium) }
                        if (p.expectedResult.isNotBlank()) Text("Done when: ${p.expectedResult}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { using = p.id }) { Text("Use it") }
                            TextButton(onClick = { deleting = p.id }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }

    using?.let { id ->
        val p = procs?.firstOrNull { it.id == id }
        if (p == null) using = null else UseChecklistDialog(vm, p, onDismiss = { using = null }) { pid, iid ->
            using = null
            openInvestigation(pid, iid)
        }
    }
    deleting?.let { id ->
        ConfirmDialog("Delete this checklist?", "Tests already made from it are kept. Only the saved checklist is removed.", "Delete",
            destructive = true, onConfirm = { vm.launch { vm.repo.deleteProcedure(id) } }, onDismiss = { deleting = null })
    }
    if (creating) NewChecklistDialog(onDismiss = { creating = false }) { title, steps, done ->
        creating = false
        val now = System.currentTimeMillis()
        vm.launch { vm.repo.saveProcedure(Procedure(vm.repo.newId(), title, steps = steps, expectedResult = done, createdAt = now, updatedAt = now)) }
    }
}

@Composable
private fun UseChecklistDialog(vm: MainViewModel, p: Procedure, onDismiss: () -> Unit, onDone: (String, String) -> Unit) {
    val lingo = LocalLingo.current
    val projects by remember { vm.repo.observeProjects() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val invs by remember { vm.repo.observeAllInvestigations() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var pid by rememberSaveable { mutableStateOf<String?>(vm.settings.lastProjectId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Use \"${p.title}\"") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Pick the ${lingo.investigation.lowercase()} to add it to as a new ${lingo.plan.lowercase()}.", style = MaterialTheme.typography.bodySmall)
                projects.filter { !it.isSample }.forEach { proj ->
                    OutlinedButton(onClick = { pid = proj.id }) {
                        Text((if (proj.id == pid) "▸ " else "") + "${ProjectTemplates.byId(proj.templateId).emoji} ${proj.title}")
                    }
                    if (proj.id == pid) {
                        invs.filter { it.projectId == proj.id && it.status != InvestigationStatus.ARCHIVED }.forEach { inv ->
                            TextButton(onClick = {
                                vm.launch { vm.repo.usePlanFromProcedure(p.id, proj.id, inv.id); vm.message = "Added to ${inv.title}."; onDone(proj.id, inv.id) }
                            }) { Text("→ ${inv.title}") }
                        }
                        TextButton(onClick = {
                            vm.launch {
                                val iid = vm.repo.createInvestigation(proj.id, p.title, p.description, p.expectedResult)
                                vm.repo.usePlanFromProcedure(p.id, proj.id, iid)
                                onDone(proj.id, iid)
                            }
                        }) { Text("+ New ${lingo.investigation.lowercase()} for this") }
                    }
                }
                if (projects.none { !it.isSample }) Tag("CREATE A PROJECT FIRST", Tone.WARN)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NewChecklistDialog(onDismiss: () -> Unit, onSave: (String, List<String>, String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var steps by rememberSaveable { mutableStateOf("") }
    var done by rememberSaveable { mutableStateOf("") }
    val parsed = steps.lines().map { it.trim().removePrefix("-").trim() }.filter { it.isNotEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New checklist") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Name", title, { title = it }, singleLine = true, placeholder = "Router reset")
                Field("Steps, one per line", steps, { steps = it }, minLines = 5)
                Field("Done when… (optional)", done, { done = it }, placeholder = "Internet light is solid green")
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank() && parsed.isNotEmpty(), onClick = { onSave(title.trim(), parsed, done.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
