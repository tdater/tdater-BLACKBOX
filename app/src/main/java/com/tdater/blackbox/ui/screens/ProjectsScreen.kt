package com.tdater.blackbox.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import com.tdater.blackbox.ui.components.HazardStripe
import com.tdater.blackbox.ui.components.TraceGlyph
import com.tdater.blackbox.ui.theme.BbColors
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
import androidx.compose.material.icons.filled.Info
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
fun ProjectsScreen(vm: MainViewModel, open: (String) -> Unit, openSettings: () -> Unit, openSearch: () -> Unit, openHelp: () -> Unit) {
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
            BbTopBar("BLACKBOX", "Flight recorder for debugging", onBack = null) {
                IconButton(onClick = openHelp) { Icon(Icons.Default.Info, contentDescription = "Guide and FAQ") }
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
                    TextButton(onClick = openHelp) { Text("New here? Read the guide") }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(320.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        HeroHeader(list.size, investigations.count { it.status != InvestigationStatus.RESOLVED && it.status != InvestigationStatus.ARCHIVED })
                    }
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
    val accent = when {
        p.isSample -> BbColors.Warn
        active.isNotEmpty() -> BbColors.Orange
        else -> BbColors.Cyan
    }
    InfoCard(Modifier.clickable(onClick = onClick), accent = accent) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            if (p.isSample) Tag("SAMPLE · SYNTHETIC", Tone.WARN)
            Tag(if (p.privacyPolicy == PrivacyPolicy.LOCAL_ONLY) "LOCAL ONLY" else "AI ALLOWED",
                if (p.privacyPolicy == PrivacyPolicy.LOCAL_ONLY) Tone.NEUTRAL else Tone.INFO)
            Tag(p.status.name)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 12.dp)) {
                Text("${active.size}", style = MaterialTheme.typography.headlineMedium, color = accent)
                Text("OPEN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (p.objective.isNotBlank()) Text(p.objective, style = MaterialTheme.typography.bodySmall, maxLines = 2,
            overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text("${active.size} open investigation(s) · updated ${fmtTime(p.updatedAt)}", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Top) {
            Text("NEXT ▸ ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(next?.nextAction ?: "no next action recorded", style = MaterialTheme.typography.bodyMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HeroHeader(projects: Int, open: Int) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            TraceGlyph(size = 64.dp)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("Your projects remember", style = MaterialTheme.typography.headlineSmall)
                Text("what happened, what changed, what's next", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            StatChip("$projects", "PROJECTS", BbColors.Cyan)
            StatChip("$open", "OPEN INVESTIGATIONS", BbColors.Orange)
        }
        HazardStripe(Modifier.padding(top = 6.dp).clip(MaterialTheme.shapes.extraSmall))
    }
}

@Composable
private fun StatChip(value: String, label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.background(color.copy(alpha = 0.12f), MaterialTheme.shapes.small)
            .border(1.dp, color.copy(alpha = 0.4f), MaterialTheme.shapes.small).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        Text("  $label", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
