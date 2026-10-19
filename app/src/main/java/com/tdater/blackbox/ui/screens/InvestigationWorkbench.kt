package com.tdater.blackbox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tdater.blackbox.core.archive.ReferenceIntegrity
import com.tdater.blackbox.core.model.ClaimClassification
import com.tdater.blackbox.core.model.Evidence
import com.tdater.blackbox.core.model.Investigation
import com.tdater.blackbox.core.model.InvestigationStatus
import com.tdater.blackbox.core.model.ProjectBundle
import com.tdater.blackbox.core.model.ReviewStatus
import com.tdater.blackbox.core.model.RunOutcome
import com.tdater.blackbox.core.model.TestPlan
import com.tdater.blackbox.core.resume.Freshness
import com.tdater.blackbox.core.resume.ResumeGenerator
import com.tdater.blackbox.ui.MainViewModel
import com.tdater.blackbox.ui.Routes
import com.tdater.blackbox.ui.components.Field
import com.tdater.blackbox.ui.components.InfoCard
import com.tdater.blackbox.ui.components.LabeledValue
import com.tdater.blackbox.ui.components.SectionHeader
import com.tdater.blackbox.ui.components.Tag
import com.tdater.blackbox.ui.components.Tone
import com.tdater.blackbox.ui.components.claimLabel
import com.tdater.blackbox.ui.components.claimTone
import com.tdater.blackbox.ui.components.fmtTime
import com.tdater.blackbox.ui.components.outcomeLabel
import com.tdater.blackbox.ui.components.outcomeTone
import com.tdater.blackbox.ui.components.shortId

private val TABS = listOf("Overview", "Evidence", "Tests", "Claims", "Timeline")

@Composable
fun InvestigationWorkbench(vm: MainViewModel, b: ProjectBundle, inv: Investigation, nav: NavHostController, modifier: Modifier = Modifier) {
    var tab by rememberSaveable(inv.id) { mutableIntStateOf(0) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var startPlan by rememberSaveable { mutableStateOf<String?>(null) }
    var addingClaim by rememberSaveable { mutableStateOf(false) }
    val pid = b.project.id

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                Tag(inv.status.name)
                Tag("rev ${inv.revision}")
            }
            Text(inv.title, style = MaterialTheme.typography.titleLarge)
            Text("Question: " + inv.currentQuestion.ifBlank { "none recorded" }, style = MaterialTheme.typography.bodyMedium)
            Text("Next action: " + inv.nextAction.ifBlank { "none recorded" }, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp)) {
                Button(onClick = { nav.navigate(Routes.capture(pid, inv.id)) }, modifier = Modifier.padding(end = 8.dp)) { Text("Capture") }
                OutlinedAction("Edit") { editing = true }
                OutlinedAction("New test plan") { nav.navigate(Routes.plan(pid, inv.id)) }
                OutlinedAction("Resume") { nav.navigate(Routes.resume(pid, inv.id)) }
                OutlinedAction("Compare runs") { nav.navigate(Routes.compare(pid, inv.id)) }
                OutlinedAction("Fix request") { nav.navigate(Routes.handoff(pid, inv.id)) }
                OutlinedAction("Ask on-device AI") { nav.navigate(Routes.ai(pid, inv.id)) }
            }
        }
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            TABS.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            when (tab) {
                0 -> overview(b, inv, nav)
                1 -> evidenceTab(b, inv, nav)
                2 -> testsTab(vm, b, inv, nav) { startPlan = it }
                3 -> claimsTab(vm, b, inv) { addingClaim = true }
                else -> timelineTab(b, inv)
            }
        }
    }

    if (editing) EditInvestigationDialog(inv, onDismiss = { editing = false }) { vm.launch { vm.repo.updateInvestigation(it) } }
    startPlan?.let { planId ->
        val plan = b.plans.firstOrNull { it.id == planId }
        if (plan != null) StartRunDialog(vm, b, plan, onDismiss = { startPlan = null }) { runId ->
            startPlan = null
            nav.navigate(Routes.run(pid, runId))
        } else startPlan = null
    }
    if (addingClaim) AddClaimDialog(b, inv, onDismiss = { addingClaim = false }) { text, cls, ev, runs ->
        addingClaim = false
        vm.launch { vm.repo.addClaim(pid, inv.id, text, cls, ev, runs) }
    }
}

private fun LazyListScope.overview(b: ProjectBundle, inv: Investigation, nav: NavHostController) {
    item {
        val capsule = b.capsules.filter { it.investigationId == inv.id }.maxByOrNull { it.generatedAt }
        SectionHeader("Resume")
        InfoCard(Modifier.clickable { nav.navigate(Routes.resume(b.project.id, inv.id)) }) {
            if (capsule == null) Text("No resume capsule saved yet. Open Resume to generate one from your records.")
            else {
                val fresh = ResumeGenerator.freshness(capsule, inv)
                Tag(if (fresh == Freshness.CURRENT) "CURRENT" else "STALE: records changed since ${fmtTime(capsule.generatedAt)}",
                    if (fresh == Freshness.CURRENT) Tone.GOOD else Tone.WARN)
                LabeledValue("Current failure", capsule.currentFailure)
                LabeledValue("Next action", capsule.nextAction)
            }
        }
        SectionHeader("Problem")
        Text(inv.problemStatement.ifBlank { "Not recorded." })
        SectionHeader("Desired outcome")
        Text(inv.desiredOutcome.ifBlank { "Not recorded." })
        val known = inv.knownGoodReferenceId?.let { id -> b.runs.firstOrNull { it.id == id } }
        SectionHeader("Known-good reference")
        Text(when {
            known != null -> "${known.planSnapshot.title} · ${known.outcome.name} · run ${shortId(known.id)}"
            inv.knownGoodReferenceId != null -> "Reference ${shortId(inv.knownGoodReferenceId!!)} no longer exists."
            else -> ResumeGenerator.NO_KNOWN_GOOD
        })
        val open = b.runs.filter { it.investigationId == inv.id && it.outcome == RunOutcome.NOT_FINISHED }
        if (open.isNotEmpty()) {
            SectionHeader("Unfinished runs")
            open.forEach { r ->
                InfoCard(Modifier.padding(vertical = 4.dp).clickable { nav.navigate(Routes.run(b.project.id, r.id)) }) {
                    Tag(outcomeLabel(r.outcome), outcomeTone(r.outcome))
                    Text("${r.planSnapshot.title} · started ${fmtTime(r.startedAt)} — tap to resume")
                }
            }
        }
        val dangling = ReferenceIntegrity.check(b).filter { d -> b.claims.any { it.id == d.claimId && it.investigationId == inv.id } }
        if (dangling.isNotEmpty()) {
            SectionHeader("Integrity warnings")
            dangling.forEach { Text("⚠ Claim ${shortId(it.claimId)} cites ${shortId(it.missingId)}: ${it.reason}", color = MaterialTheme.colorScheme.error) }
        }
    }
}

private fun LazyListScope.evidenceTab(b: ProjectBundle, inv: Investigation, nav: NavHostController) {
    val ev = b.evidence.filter { it.investigationId == inv.id }.sortedByDescending { it.capturedAt }
    if (ev.isEmpty()) item {
        Text("No evidence yet. Use Capture to add a note, photo, file, log or voice note.",
            modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    items(ev, key = { it.id }) { e -> EvidenceRow(b, e) { nav.navigate(Routes.evidence(b.project.id, e.id)) } }
}

@Composable
fun EvidenceRow(b: ProjectBundle, e: Evidence, onClick: () -> Unit) {
    InfoCard(Modifier.padding(vertical = 4.dp).clickable(onClick = onClick)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag(e.kind.name, Tone.INFO)
            if (e.withdrawnAt != null) Tag("WITHDRAWN", Tone.BAD)
            e.testRunId?.let { rid -> b.runs.firstOrNull { it.id == rid }?.let { Tag("run ${shortId(it.id)}") } }
        }
        Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (e.text.isNotBlank()) Text(e.text, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        Text("${fmtTime(e.capturedAt)} · ${e.source.name.lowercase()}" + (e.attachmentBytes?.let { " · ${it / 1024} KB" } ?: ""),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun LazyListScope.testsTab(vm: MainViewModel, b: ProjectBundle, inv: Investigation, nav: NavHostController, start: (String) -> Unit) {
    val plans = b.plans.filter { it.investigationId == inv.id }
    item {
        SectionHeader("Test plans") { TextButton(onClick = { nav.navigate(Routes.plan(b.project.id, inv.id)) }) { Text("+ New plan") } }
        if (plans.isEmpty()) Text("No plans yet. A plan holds the steps and the expected result so every run is comparable.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    items(plans, key = { it.id }) { p ->
        val open = b.runs.firstOrNull { it.testPlanId == p.id && it.outcome == RunOutcome.NOT_FINISHED }
        InfoCard(Modifier.padding(vertical = 4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag("r${p.revision}")
                if (p.isRegression) Tag("REGRESSION CHECK", Tone.INFO)
            }
            Text(p.title, style = MaterialTheme.typography.titleSmall)
            Text("Expect: ${p.expectedResult}", style = MaterialTheme.typography.bodySmall)
            Text("${p.steps.size} step(s)", style = MaterialTheme.typography.labelSmall)
            Row {
                if (open != null) Button(onClick = { nav.navigate(Routes.run(b.project.id, open.id)) }, modifier = Modifier.padding(end = 8.dp)) { Text("Resume run") }
                else Button(onClick = { start(p.id) }, modifier = Modifier.padding(end = 8.dp)) { Text("Start run") }
                OutlinedButton(onClick = { nav.navigate(Routes.plan(b.project.id, inv.id, p.id)) }) { Text("Revise") }
            }
        }
    }
    val runs = b.runs.filter { it.investigationId == inv.id }.sortedByDescending { it.startedAt }
    item { SectionHeader("Runs (${runs.size})") }
    items(runs, key = { it.id }) { r ->
        InfoCard(Modifier.padding(vertical = 4.dp).clickable { nav.navigate(Routes.run(b.project.id, r.id)) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(outcomeLabel(r.outcome), outcomeTone(r.outcome))
                if (inv.knownGoodReferenceId == r.id) Tag("★ KNOWN-GOOD", Tone.GOOD)
                Tag("plan r${r.planSnapshot.revision}")
            }
            Text("${r.planSnapshot.title} · ${fmtTime(r.startedAt)} · run ${shortId(r.id)}", style = MaterialTheme.typography.bodySmall)
            if (r.intendedChange.isNotBlank()) Text("Change: ${r.intendedChange}", style = MaterialTheme.typography.bodySmall)
            if (r.actualResult.isNotBlank()) Text("Result: ${r.actualResult}", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun LazyListScope.claimsTab(vm: MainViewModel, b: ProjectBundle, inv: Investigation, add: () -> Unit) {
    val claims = b.claims.filter { it.investigationId == inv.id }
    item {
        SectionHeader("Claims") { TextButton(onClick = add) { Text("+ Add claim") } }
        Text("Observed = you saw it. Inferred = a hypothesis. Supported = established by a recorded test. Accepting a claim records your review, not certainty.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    items(claims, key = { it.id }) { c ->
        InfoCard(Modifier.padding(vertical = 4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(claimLabel(c.classification), claimTone(c.classification))
                Tag(c.reviewStatus.name, if (c.reviewStatus == ReviewStatus.REJECTED) Tone.BAD else Tone.NEUTRAL)
            }
            Text(c.text)
            val refs = c.evidenceIds.map { "evidence ${shortId(it)}" } + c.testRunIds.map { "run ${shortId(it)}" }
            Text(if (refs.isEmpty()) "No supporting records linked." else "Sources: " + refs.joinToString(), style = MaterialTheme.typography.labelSmall)
            Row {
                TextButton(onClick = { vm.launch { vm.repo.setClaimReview(c, ReviewStatus.ACCEPTED) } }) { Text("Accept") }
                TextButton(onClick = { vm.launch { vm.repo.setClaimReview(c, ReviewStatus.REJECTED) } }) { Text("Reject") }
            }
        }
    }
}

private fun LazyListScope.timelineTab(b: ProjectBundle, inv: Investigation) {
    val events = b.timeline.filter { it.investigationId == inv.id }.sortedByDescending { it.timestamp }
    if (events.isEmpty()) item { Text("No events yet.", modifier = Modifier.padding(vertical = 16.dp)) }
    items(events, key = { it.id }) { t ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(fmtTime(t.timestamp), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column {
                Text(t.summary, style = MaterialTheme.typography.bodyMedium)
                t.sourceTimestamp?.let { Text("source time ${fmtTime(it)}", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
private fun EditInvestigationDialog(inv: Investigation, onDismiss: () -> Unit, onSave: (Investigation) -> Unit) {
    var title by rememberSaveable { mutableStateOf(inv.title) }
    var problem by rememberSaveable { mutableStateOf(inv.problemStatement) }
    var desired by rememberSaveable { mutableStateOf(inv.desiredOutcome) }
    var question by rememberSaveable { mutableStateOf(inv.currentQuestion) }
    var next by rememberSaveable { mutableStateOf(inv.nextAction) }
    var status by rememberSaveable { mutableStateOf(inv.status) }
    var pickStatus by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit investigation") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Field("Title", title, { title = it }, singleLine = true) }
                item { Field("Problem statement", problem, { problem = it }, minLines = 2) }
                item { Field("Desired outcome", desired, { desired = it }) }
                item { Field("Current question", question, { question = it }) }
                item { Field("Next action", next, { next = it }) }
                item { OutlinedButton(onClick = { pickStatus = true }) { Text("Status: ${status.name}") } }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                onSave(inv.copy(title = title.trim(), problemStatement = problem, desiredOutcome = desired,
                    currentQuestion = question, nextAction = next, status = status))
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pickStatus) ChoiceDialog("Status", InvestigationStatus.entries, status, { it.name }, { status = it }, { pickStatus = false })
}

@Composable
private fun StartRunDialog(vm: MainViewModel, b: ProjectBundle, plan: TestPlan, onDismiss: () -> Unit, onStarted: (String) -> Unit) {
    val builds = b.builds.sortedByDescending { it.createdAt }
    val envs = b.environments.sortedByDescending { it.capturedAt }
    var buildId by rememberSaveable { mutableStateOf(builds.firstOrNull()?.id) }
    var envId by rememberSaveable { mutableStateOf(envs.firstOrNull()?.id) }
    var change by rememberSaveable { mutableStateOf("") }
    var pickBuild by rememberSaveable { mutableStateOf(false) }
    var pickEnv by rememberSaveable { mutableStateOf(false) }
    var starting by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start run: ${plan.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Plan revision r${plan.revision} will be frozen into this run.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { pickBuild = true }) {
                    Text("Build: " + (builds.firstOrNull { it.id == buildId }?.displayName ?: "not recorded"), maxLines = 1)
                }
                OutlinedButton(onClick = { pickEnv = true }) {
                    Text("Environment: " + (envs.firstOrNull { it.id == envId }?.let { "snapshot ${fmtTime(it.capturedAt)}" } ?: "not recorded"), maxLines = 1)
                }
                if (builds.isEmpty() || envs.isEmpty()) Text("Add builds and environment snapshots from the project menu (⋮ → Builds & environments).",
                    style = MaterialTheme.typography.labelSmall)
                Field("What is different this time? (intended change)", change, { change = it }, minLines = 2)
            }
        },
        confirmButton = {
            TextButton(enabled = !starting, onClick = {
                starting = true
                vm.launch { onStarted(vm.repo.startRun(plan.id, buildId, envId, change)) }
            }) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pickBuild) ChoiceDialog("Build", listOf<String?>(null) + builds.map { it.id }, buildId,
        { id -> builds.firstOrNull { it.id == id }?.let { "${it.displayName} (${it.versionName ?: "?"})" } ?: "Not recorded" },
        { buildId = it }, { pickBuild = false })
    if (pickEnv) ChoiceDialog("Environment snapshot", listOf<String?>(null) + envs.map { it.id }, envId,
        { id -> envs.firstOrNull { it.id == id }?.let { "${fmtTime(it.capturedAt)} · ${it.fields.size} fields · ${it.source}" } ?: "Not recorded" },
        { envId = it }, { pickEnv = false })
}

@Composable
private fun AddClaimDialog(b: ProjectBundle, inv: Investigation, onDismiss: () -> Unit,
                           onAdd: (String, ClaimClassification, List<String>, List<String>) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var cls by rememberSaveable { mutableStateOf(ClaimClassification.OBSERVED) }
    var ev by rememberSaveable { mutableStateOf(listOf<String>()) }
    var runs by rememberSaveable { mutableStateOf(listOf<String>()) }
    val evidence = b.evidence.filter { it.investigationId == inv.id && it.withdrawnAt == null }
    val finished = b.runs.filter { it.investigationId == inv.id && it.isFinished }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add claim") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { Field("Claim", text, { text = it }, minLines = 2) }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        ClaimClassification.entries.forEach { c ->
                            FilterChip(selected = cls == c, onClick = { cls = c }, label = { Text(claimLabel(c)) }, modifier = Modifier.padding(end = 6.dp))
                        }
                    }
                    if (cls == ClaimClassification.SUPPORTED_RESULT && runs.isEmpty())
                        Text("A supported result must cite at least one finished run.", color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall)
                }
                item { Text("Link evidence", style = MaterialTheme.typography.labelLarge) }
                items(evidence, key = { it.id }) { e ->
                    FilterChip(selected = e.id in ev, onClick = { ev = if (e.id in ev) ev - e.id else ev + e.id },
                        label = { Text("${e.kind.name}: ${e.title}", maxLines = 1) })
                }
                item { Text("Link runs", style = MaterialTheme.typography.labelLarge) }
                items(finished, key = { it.id }) { r ->
                    FilterChip(selected = r.id in runs, onClick = { runs = if (r.id in runs) runs - r.id else runs + r.id },
                        label = { Text("${r.outcome.name} · ${r.planSnapshot.title} · ${shortId(r.id)}", maxLines = 1) })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank() && !(cls == ClaimClassification.SUPPORTED_RESULT && runs.isEmpty()),
                onClick = { onAdd(text, cls, ev, runs) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
