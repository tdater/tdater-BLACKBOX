package com.tdater.blackbox.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.tdater.blackbox.core.templates.ProjectTemplate
import com.tdater.blackbox.core.templates.ProjectTemplates
import com.tdater.blackbox.system.QuickCapture
import com.tdater.blackbox.ui.components.LocalLingo
import com.tdater.blackbox.ui.theme.LocalPalette
import kotlinx.coroutines.delay
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
fun ProjectsScreen(vm: MainViewModel, open: (String, String?) -> Unit, openSettings: () -> Unit, openSearch: () -> Unit,
                   openHelp: () -> Unit, quick: (String) -> Unit, openChecklists: () -> Unit) {
    val projects by remember { vm.repo.observeProjects() }.collectAsStateWithLifecycle(initialValue = null)
    val investigations by remember { vm.repo.observeAllInvestigations() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val followUps by remember { vm.repo.observeFollowUps() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var creatingWith by rememberSaveable { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val techy = LocalPalette.current.techy
    val lingo = LocalLingo.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }

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
                open(pid, null)
            } finally {
                stage.deleteRecursively()
            }
        }
    }

    fun openProject(p: Project, iid: String? = null) {
        val share = vm.pendingShare
        if (share != null) {
            vm.pendingShare = null
            vm.launch("Saving shared item failed") {
                share.uris.forEach { vm.repo.importEvidence(ctx.contentResolver, it, p.id, null, null) }
                if (!share.text.isNullOrBlank()) vm.repo.addTextEvidence(p.id, null, null, EvidenceKind.TEXT, share.subject ?: "", share.text)
                vm.message = "Shared item saved to ${p.title}."
            }
        }
        vm.settings.lastProjectId = p.id
        open(p.id, iid)
    }

    Scaffold(
        topBar = {
            BbTopBar("BLACKBOX", if (techy) "Flight recorder for debugging" else "Figure it out. Remember what worked.", onBack = null) {
                IconButton(onClick = openHelp) { Icon(Icons.Default.Info, contentDescription = "Guide and FAQ") }
                IconButton(onClick = openSearch) { Icon(Icons.Default.Search, contentDescription = "Search records") }
                IconButton(onClick = openSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
            }
        },
        floatingActionButton = {
            if (!projects.isNullOrEmpty()) ExtendedFloatingActionButton(onClick = { creatingWith = ProjectTemplates.CUSTOM },
                icon = { Icon(Icons.Default.Add, null) }, text = { Text("New project") })
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
                list.isEmpty() -> Welcome(
                    pick = { creatingWith = it },
                    loadSample = { vm.launch { open(vm.repo.loadSample(), null) } },
                    importArchive = { importArchive.launch(arrayOf("application/zip", "application/octet-stream")) },
                    openHelp = openHelp,
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(320.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val openInvs = investigations.filter { it.status != InvestigationStatus.RESOLVED && it.status != InvestigationStatus.ARCHIVED }
                    full { HeroHeader(list.size, openInvs.size, techy, lingo.openCount) }
                    full { QuickActions(quick, openChecklists) }
                    val due = followUps.filter { (it.followUpAt ?: Long.MAX_VALUE) <= now + 7 * 24 * 3600_000L }
                    if (due.isNotEmpty()) full {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            com.tdater.blackbox.ui.components.SectionHeader("Follow-ups")
                            due.take(4).forEach { inv ->
                                val p = list.firstOrNull { it.id == inv.projectId }
                                val overdue = (inv.followUpAt ?: 0) <= now
                                InfoCard(Modifier.clickable { p?.let { openProject(it, inv.id) } }, accent = if (overdue) BbColors.Warn else BbColors.Cyan) {
                                    Tag(if (overdue) "DUE" else "COMING UP · ${fmtTime(inv.followUpAt)}", if (overdue) Tone.WARN else Tone.INFO)
                                    Text(inv.title, style = MaterialTheme.typography.titleMedium)
                                    Text((p?.title ?: "") + (inv.followUpNote.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    val recent = openInvs.maxByOrNull { it.updatedAt }
                    val recentProject = recent?.let { r -> list.firstOrNull { it.id == r.projectId } }
                    if (recent != null && recentProject != null) full {
                        ContinueCard(recentProject, recent) { openProject(recentProject, recent.id) }
                    }
                    full { com.tdater.blackbox.ui.components.SectionHeader("Projects") }
                    items(list, key = { it.id }) { p ->
                        ProjectCard(p, investigations.filter { it.projectId == p.id }) { openProject(p) }
                    }
                    full {
                        Column {
                            com.tdater.blackbox.ui.components.SectionHeader("Start something new")
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ProjectTemplates.all.forEach { t ->
                                    OutlinedButton(onClick = { creatingWith = t.id }) { Text("${t.emoji}  ${t.name}") }
                                }
                            }
                            TextButton(onClick = { importArchive.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                                Text("Import project archive…")
                            }
                        }
                    }
                }
            }
        }
    }

    creatingWith?.let { tid ->
        NewProjectDialog(tid, onDismiss = { creatingWith = null }) { title, objective, privacy, template ->
            creatingWith = null
            vm.launch {
                val pid = vm.repo.createProject(title, objective, privacy, template)
                vm.settings.lastProjectId = pid
                open(pid, null)
            }
        }
    }
}

private fun LazyGridScope.full(content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun Welcome(pick: (String) -> Unit, loadSample: () -> Unit, importArchive: () -> Unit, openHelp: () -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(156.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        full {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                TraceGlyph(size = 88.dp)
                Text("What are you working on?", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text("Pick a starting point. BLACKBOX keeps the problem, what you tried, what changed and what to do next, all on this phone. No account needed.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 520.dp).padding(top = 6.dp))
            }
        }
        items(ProjectTemplates.all, key = { it.id }) { t -> TemplateTile(t) { pick(t.id) } }
        full {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                OutlinedButton(onClick = loadSample) { Text("Load labeled sample (synthetic data)") }
                TextButton(onClick = importArchive) { Text("Import project archive…") }
                TextButton(onClick = openHelp) { Text("New here? Read the guide") }
            }
        }
    }
}

@Composable
private fun TemplateTile(t: ProjectTemplate, onClick: () -> Unit) {
    val accent = Color(t.accent)
    InfoCard(Modifier.clickable(onClick = onClick), accent = accent) {
        Avatar(t.emoji, accent, 44.dp)
        Text(t.name, style = MaterialTheme.typography.titleMedium)
        Text(t.tagline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
            overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Avatar(emoji: String, accent: Color, size: androidx.compose.ui.unit.Dp = 40.dp) {
    androidx.compose.foundation.layout.Box(
        Modifier.size(size).background(accent.copy(alpha = 0.18f), CircleShape).border(1.dp, accent.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, style = MaterialTheme.typography.titleLarge) }
}

@Composable
private fun QuickActions(quick: (String) -> Unit, openChecklists: () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(QuickCapture.NOTE to "✎  Quick note", QuickCapture.PHOTO to "◉  Photo", QuickCapture.VOICE to "●  Voice note",
            QuickCapture.READING to "≋  Log a reading").forEach { (mode, label) ->
            FilterChip(selected = false, onClick = { quick(mode) }, label = { Text(label) }, modifier = Modifier.height(44.dp))
        }
        FilterChip(selected = false, onClick = openChecklists, label = { Text("☑  Checklists") }, modifier = Modifier.height(44.dp))
    }
}

@Composable
private fun ContinueCard(p: Project, inv: Investigation, onClick: () -> Unit) {
    val lingo = LocalLingo.current
    val t = ProjectTemplates.byId(p.templateId)
    InfoCard(Modifier.clickable(onClick = onClick), accent = BbColors.Orange) {
        Text("PICK UP WHERE YOU LEFT OFF", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(t.emoji, Color(t.accent))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(inv.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${p.title} · ${fmtTime(inv.updatedAt)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            Text("${lingo.nextAction.uppercase()} ▸ ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(inv.nextAction.ifBlank { "not written down yet" }, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ProjectCard(p: Project, invs: List<Investigation>, onClick: () -> Unit) {
    val lingo = LocalLingo.current
    val t = ProjectTemplates.byId(p.templateId)
    val active = invs.filter { it.status != InvestigationStatus.RESOLVED && it.status != InvestigationStatus.ARCHIVED }
    val next = active.firstOrNull { it.nextAction.isNotBlank() }
    val accent = when {
        p.isSample -> BbColors.Warn
        p.templateId == ProjectTemplates.CUSTOM -> if (active.isNotEmpty()) BbColors.Orange else BbColors.Cyan
        else -> Color(t.accent)
    }
    InfoCard(Modifier.clickable(onClick = onClick), accent = accent) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            if (p.isSample) Tag("SAMPLE · SYNTHETIC", Tone.WARN)
            Tag(t.name.uppercase(), Tone.NEUTRAL)
            if (p.privacyPolicy == PrivacyPolicy.AI_ALLOWED) Tag("AI ALLOWED", Tone.INFO)
            if (p.status != com.tdater.blackbox.core.model.ProjectStatus.ACTIVE) Tag(p.status.name)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(if (p.isSample) "🧪" else t.emoji, accent)
            Text(p.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 12.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 12.dp)) {
                Text("${active.size}", style = MaterialTheme.typography.headlineMedium, color = accent)
                Text("OPEN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (p.objective.isNotBlank()) Text(p.objective, style = MaterialTheme.typography.bodySmall, maxLines = 2,
            overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text("${active.size} open ${lingo.investigations.lowercase()} · updated ${fmtTime(p.updatedAt)}", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Top) {
            Text("${lingo.nextAction.uppercase()} ▸ ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(next?.nextAction ?: "nothing written down yet", style = MaterialTheme.typography.bodyMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HeroHeader(projects: Int, open: Int, techy: Boolean, openLabel: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            TraceGlyph(size = 64.dp)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(if (techy) "Your projects remember" else greeting(), style = MaterialTheme.typography.headlineSmall)
                Text(if (techy) "what happened, what changed, what's next" else "Here's what's still open and what to do next.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            StatChip("$projects", "PROJECTS", BbColors.Cyan)
            StatChip("$open", openLabel, BbColors.Orange)
        }
        if (techy) HazardStripe(Modifier.padding(top = 6.dp).clip(MaterialTheme.shapes.extraSmall))
    }
}

private fun greeting(): String = when (java.time.LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
private fun StatChip(value: String, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.background(color.copy(alpha = 0.12f), MaterialTheme.shapes.small)
            .border(1.dp, color.copy(alpha = 0.4f), MaterialTheme.shapes.small).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        Text("  $label", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun NewProjectDialog(initialTemplate: String, onDismiss: () -> Unit, onCreate: (String, String, PrivacyPolicy, String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var objective by rememberSaveable { mutableStateOf("") }
    var aiAllowed by rememberSaveable { mutableStateOf(false) }
    var template by rememberSaveable { mutableStateOf(initialTemplate) }
    val t = ProjectTemplates.byId(template)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProjectTemplates.all.forEach { o ->
                        FilterChip(selected = o.id == template, onClick = { template = o.id }, label = { Text("${o.emoji} ${o.name}") })
                    }
                }
                Text(t.tagline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Field("Name", title, { title = it }, singleLine = true, placeholder = t.titleHint.ifBlank { "e.g. Garage door" })
                Field("Goal (optional)", objective, { objective = it }, minLines = 2)
                if (t.fields.isNotEmpty()) Text("Will track: " + t.fields.joinToString(", ") + ". You can change these later.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                t.note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = BbColors.Warn) }
                Row(Modifier.fillMaxWidth().clickable { aiAllowed = !aiAllowed }.padding(vertical = 4.dp)) {
                    androidx.compose.material3.Checkbox(checked = aiAllowed, onCheckedChange = { aiAllowed = it })
                    Text("Let the on-device AI read records I pick (default: off)",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                onCreate(title, objective, if (aiAllowed) PrivacyPolicy.AI_ALLOWED else PrivacyPolicy.LOCAL_ONLY, template)
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

