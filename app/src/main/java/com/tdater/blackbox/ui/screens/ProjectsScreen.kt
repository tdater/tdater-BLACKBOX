package com.tdater.blackbox.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tdater.blackbox.core.archive.ArchiveException
import com.tdater.blackbox.core.model.EvidenceKind
import com.tdater.blackbox.core.model.Investigation
import com.tdater.blackbox.core.model.InvestigationStatus
import com.tdater.blackbox.core.model.PrivacyPolicy
import com.tdater.blackbox.core.model.Project
import com.tdater.blackbox.ui.MainViewModel
import com.tdater.blackbox.ui.components.BbTopBar
import com.tdater.blackbox.ui.components.EmptyState
import com.tdater.blackbox.ui.components.Field
import com.tdater.blackbox.ui.components.InfoCard
import com.tdater.blackbox.ui.components.Tag
import com.tdater.blackbox.ui.components.Tone
import com.tdater.blackbox.ui.components.fmtTime
import java.io.File

@Composable
fun ProjectsScreen(vm: MainViewModel, open: (String) -> Unit, openSettings: () -> Unit, openSearch: () -> Unit) {
    val projects by remember { vm.repo.observeProjects() }.collectAsStateWithLifecycle(initialValue = null)
    val investigations by remember { vm.repo.observeAllInvestigations() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var creating by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current

    val importArchive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.launch("Import failed") {
            val stage = File(ctx.cacheDir, "archive-stage-${System.currentTimeMillis()}")
            try {
                val input = ctx.contentResolver.openInputStream(uri) ?: throw ArchiveException("Could not open file.")
                val (pid, read) = input.use { vm.repo.importArchive(it, stage) }
                val missing = read.manifest.missingAttachments.size
                vm.message = "Imported \"${read.manifest.projectTitle}\" as a new project" +
                    (if (missing > 0) "; $missing attachment(s) were missing in the archive." else ".")
                open(pid)
            } finally {
                stage.deleteRecursively()
            }
        }
    }

    Scaffold(
        topBar = {
            BbTopBar("BLACKBOX", "Projects remember what happened", onBack = null) {
                IconButton(onClick = openSearch) { Icon(Icons.Default.Search, contentDescription = "Search records") }
                IconButton(onClick = openSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New project") })
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            vm.pendingShare?.let { share ->
                InfoCard(Modifier.padding(12.dp)) {
                    Text("Shared item waiting", style = MaterialTheme.typography.titleSmall)
                    Text("${share.uris.size} file(s)" + (if (!share.text.isNullOrBlank()) " + text" else "") +
                        ". Tap a project below to save it there.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { vm.pendingShare = null }) { Text("Discard shared item") }
                }
            }
            val list = projects
            when {
                list == null -> EmptyState("Loading…", "Opening local database.")
                list.isEmpty() -> EmptyState(
                    "No projects yet",
                    "Create a project for the app or device you are troubleshooting. Everything stays on this phone; no account or AI key is needed.",
                ) {
                    Button(onClick = { creating = true }) { Text("Create project") }
                    OutlinedButton(onClick = { vm.launch { open(vm.repo.loadSample()) } }) { Text("Load labeled sample (synthetic data)") }
                    TextButton(onClick = { importArchive.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                        Text("Import project archive…")
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(320.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(list, key = { it.id }) { p ->
                        ProjectCard(p, investigations.filter { it.projectId == p.id }) {
                            val share = vm.pendingShare
                            if (share != null) {
                                vm.pendingShare = null
                                vm.launch("Saving shared item failed") {
                                    share.uris.forEach { vm.repo.importEvidence(ctx.contentResolver, it, p.id, null, null) }
                                    if (!share.text.isNullOrBlank()) vm.repo.addTextEvidence(p.id, null, null, EvidenceKind.TEXT,
                                        share.subject ?: "", share.text)
                                    vm.message = "Shared item saved to ${p.title}."
                                }
                            }
                            vm.settings.lastProjectId = p.id
                            open(p.id)
                        }
                    }
                    item {
                        TextButton(onClick = { importArchive.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                            Text("Import project archive…")
                        }
                    }
                }
            }
        }
    }

    if (creating) NewProjectDialog(onDismiss = { creating = false }) { title, objective, privacy ->
        creating = false
        vm.launch { open(vm.repo.createProject(title, objective, privacy)) }
    }
}

@Composable
private fun ProjectCard(p: Project, invs: List<Investigation>, onClick: () -> Unit) {
    val active = invs.filter { it.status != InvestigationStatus.RESOLVED && it.status != InvestigationStatus.ARCHIVED }
    val next = active.firstOrNull { it.nextAction.isNotBlank() }
    InfoCard(Modifier.clickable(onClick = onClick)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (p.isSample) Tag("SAMPLE · SYNTHETIC", Tone.WARN)
            Tag(if (p.privacyPolicy == PrivacyPolicy.LOCAL_ONLY) "LOCAL ONLY" else "AI ALLOWED",
                if (p.privacyPolicy == PrivacyPolicy.LOCAL_ONLY) Tone.NEUTRAL else Tone.INFO)
            Tag(p.status.name)
        }
        Text(p.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (p.objective.isNotBlank()) Text(p.objective, style = MaterialTheme.typography.bodySmall, maxLines = 2,
            overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text("${active.size} open investigation(s) · updated ${fmtTime(p.updatedAt)}", style = MaterialTheme.typography.labelMedium)
        Text("Next: " + (next?.nextAction ?: "no next action recorded"), style = MaterialTheme.typography.bodySmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun NewProjectDialog(onDismiss: () -> Unit, onCreate: (String, String, PrivacyPolicy) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var objective by rememberSaveable { mutableStateOf("") }
    var aiAllowed by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Title", title, { title = it }, singleLine = true, placeholder = "e.g. KITT launcher")
                Field("Objective (optional)", objective, { objective = it }, minLines = 2)
                Row(Modifier.fillMaxWidth().clickable { aiAllowed = !aiAllowed }.padding(vertical = 4.dp)) {
                    androidx.compose.material3.Checkbox(checked = aiAllowed, onCheckedChange = { aiAllowed = it })
                    Text("Allow selected records to be sent to an AI provider later (default: local only)",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                onCreate(title, objective, if (aiAllowed) PrivacyPolicy.AI_ALLOWED else PrivacyPolicy.LOCAL_ONLY)
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
