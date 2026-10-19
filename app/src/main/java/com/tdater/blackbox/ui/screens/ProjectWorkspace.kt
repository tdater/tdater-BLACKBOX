package com.tdater.blackbox.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tdater.blackbox.core.model.InvestigationStatus
import com.tdater.blackbox.core.model.PrivacyPolicy
import com.tdater.blackbox.core.model.ProjectBundle
import com.tdater.blackbox.ui.MainViewModel
import com.tdater.blackbox.ui.Routes
import com.tdater.blackbox.ui.components.BbTopBar
import com.tdater.blackbox.ui.components.ConfirmDialog
import com.tdater.blackbox.ui.components.EmptyState
import com.tdater.blackbox.ui.components.Field
import com.tdater.blackbox.ui.components.InfoCard
import com.tdater.blackbox.ui.components.SectionHeader
import com.tdater.blackbox.ui.components.Tag
import com.tdater.blackbox.ui.components.Tone
import com.tdater.blackbox.ui.components.fmtTime

/**
 * Project workspace. Wide windows (unfolded Fold, tablets, large resizable windows) show the
 * investigation list beside the workbench; compact windows show one at a time. The selected
 * investigation is saved state, so folding/unfolding keeps the user's place.
 */
@Composable
fun ProjectWorkspace(vm: MainViewModel, pid: String, initialIid: String?, nav: NavHostController, back: () -> Unit) {
    val bundle by rememberBundle(vm, pid)
    var selected by rememberSaveable(pid) { mutableStateOf(initialIid) }
    val layout = layoutInfo()
    val b = bundle

    if (!layout.wide && selected != null) BackHandler { selected = null }

    Scaffold(topBar = {
        BbTopBar(b?.project?.title ?: "Project", if (b?.project?.isSample == true) "SAMPLE · synthetic data" else null,
            onBack = { if (!layout.wide && selected != null) selected = null else back() }) {
            if (b != null) ProjectMenu(vm, b, nav, back)
        }
    }) { pad ->
        if (b == null) { EmptyState("Loading project…", "", Modifier.padding(pad)); return@Scaffold }
        val inv = b.investigations.firstOrNull { it.id == selected }
        if (layout.wide) {
            Row(Modifier.padding(pad).fillMaxSize()) {
                InvestigationList(vm, b, selected, Modifier.width(if (layout.expanded) 340.dp else 280.dp).fillMaxHeight()) { selected = it }
                VerticalDivider()
                if (inv != null) InvestigationWorkbench(vm, b, inv, nav, Modifier.weight(1f))
                else EmptyState("Select an investigation", "Pick one on the left or start a new one.", Modifier.weight(1f))
            }
        } else {
            if (inv != null) InvestigationWorkbench(vm, b, inv, nav, Modifier.padding(pad))
            else InvestigationList(vm, b, selected, Modifier.padding(pad).fillMaxSize()) { selected = it }
        }
    }
}

@Composable
private fun ProjectMenu(vm: MainViewModel, b: ProjectBundle, nav: NavHostController, back: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.launch("Export failed") {
            val res = ctx.contentResolver.openOutputStream(uri)?.use { vm.repo.exportArchive(b.project.id, it) }
                ?: throw IllegalStateException("Could not write to the chosen location")
            val m = res.manifest
            vm.message = "Exported ${m.recordCounts.values.sum()} records and ${m.files.size} attachment(s)" +
                if (m.missingAttachments.isNotEmpty()) "; ${m.missingAttachments.size} attachment file(s) were missing." else "."
        }
    }
    IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Project actions") }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text("Builds & environments") }, onClick = { open = false; nav.navigate(Routes.config(b.project.id)) })
        DropdownMenuItem(text = { Text("Regression checklist") }, onClick = { open = false; nav.navigate(Routes.regression(b.project.id)) })
        DropdownMenuItem(text = { Text("Quick capture (no investigation)") }, onClick = { open = false; nav.navigate(Routes.capture(b.project.id, null)) })
        DropdownMenuItem(
            text = { Text(if (b.project.privacyPolicy == PrivacyPolicy.LOCAL_ONLY) "Policy: LOCAL ONLY (tap to allow AI)" else "Policy: AI ALLOWED (tap for local only)") },
            onClick = {
                open = false
                val next = if (b.project.privacyPolicy == PrivacyPolicy.LOCAL_ONLY) PrivacyPolicy.AI_ALLOWED else PrivacyPolicy.LOCAL_ONLY
                vm.launch { vm.repo.updateProject(b.project.copy(privacyPolicy = next)) }
            })
        DropdownMenuItem(text = { Text("Export project archive (.zip)") }, onClick = {
            open = false
            export.launch("blackbox-" + b.project.title.replace(Regex("[^A-Za-z0-9]+"), "-").take(40) + ".zip")
        })
        DropdownMenuItem(text = { Text("Delete project…", color = MaterialTheme.colorScheme.error) }, onClick = { open = false; confirmDelete = true })
    }
    if (confirmDelete) ConfirmDialog(
        "Delete \"${b.project.title}\"?",
        "This permanently deletes ${b.investigations.size} investigation(s), ${b.runs.size} run(s), ${b.evidence.size} evidence record(s) and their stored attachment files from this device. Export an archive first if you may need it. This cannot be undone.",
        "Delete permanently", destructive = true,
        onConfirm = { vm.launch { vm.repo.deleteProject(b.project.id); back() } },
        onDismiss = { confirmDelete = false },
    )
}

@Composable
private fun InvestigationList(vm: MainViewModel, b: ProjectBundle, selected: String?, modifier: Modifier, onSelect: (String) -> Unit) {
    var creating by rememberSaveable { mutableStateOf(false) }
    LazyColumn(modifier.padding(horizontal = 12.dp)) {
        item {
            if (b.project.isSample) Tag("SAMPLE · SYNTHETIC DATA — not a real investigation", Tone.WARN)
            if (b.project.objective.isNotBlank()) {
                SectionHeader("Objective")
                Text(b.project.objective, style = MaterialTheme.typography.bodyMedium)
            }
            SectionHeader("Investigations") {
                TextButton(onClick = { creating = true }) { Icon(Icons.Default.Add, null); Text("New") }
            }
            if (b.investigations.isEmpty()) {
                Text("No investigations yet. Start one for a specific problem, e.g. \"OBD values freeze after screen-off\".",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { creating = true }, modifier = Modifier.padding(vertical = 8.dp)) { Text("Start investigation") }
            }
        }
        val sorted = b.investigations.sortedWith(compareBy<com.tdater.blackbox.core.model.Investigation> {
            it.status == InvestigationStatus.RESOLVED || it.status == InvestigationStatus.ARCHIVED
        }.thenByDescending { it.updatedAt })
        items(sorted, key = { it.id }) { inv ->
            val runs = b.runs.filter { it.investigationId == inv.id }
            val isSel = inv.id == selected
            InfoCard(Modifier.padding(vertical = 4.dp).clickable { onSelect(inv.id) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(inv.status.name, when (inv.status) {
                        InvestigationStatus.RESOLVED -> Tone.GOOD
                        InvestigationStatus.BLOCKED -> Tone.BAD
                        InvestigationStatus.TESTING -> Tone.INFO
                        else -> Tone.NEUTRAL
                    })
                    if (isSel) Tag("▶ OPEN", Tone.INFO)
                }
                Text(inv.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${runs.size} run(s) · updated ${fmtTime(inv.updatedAt)}", style = MaterialTheme.typography.labelSmall)
                if (inv.nextAction.isNotBlank()) Text("Next: ${inv.nextAction}", style = MaterialTheme.typography.bodySmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        item { HorizontalDivider(Modifier.padding(vertical = 12.dp)) }
        item {
            Text("${b.evidence.count { it.investigationId == null && it.withdrawnAt == null }} unassigned capture(s) in this project",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (creating) NewInvestigationDialog(onDismiss = { creating = false }) { t, p, d ->
        creating = false
        vm.launch { onSelect(vm.repo.createInvestigation(b.project.id, t, p, d)) }
    }
}

@Composable
private fun NewInvestigationDialog(onDismiss: () -> Unit, onCreate: (String, String, String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var problem by rememberSaveable { mutableStateOf("") }
    var desired by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New investigation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Title", title, { title = it }, singleLine = true, placeholder = "OBD values freeze after screen-off")
                Field("Problem statement (what happens)", problem, { problem = it }, minLines = 2)
                Field("Desired outcome", desired, { desired = it }, minLines = 1)
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onCreate(title, problem, desired) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun OutlinedAction(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)) { Text(text) }
}
