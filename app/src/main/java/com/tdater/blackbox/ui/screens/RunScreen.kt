package com.tdater.blackbox.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tdater.blackbox.core.compare.ComparisonEngine
import com.tdater.blackbox.core.model.Measurement
import com.tdater.blackbox.core.model.ProjectBundle
import com.tdater.blackbox.core.model.RunOutcome
import com.tdater.blackbox.core.model.TestRun
import com.tdater.blackbox.core.procedures.ProcedureLibrary
import com.tdater.blackbox.ui.MainViewModel
import com.tdater.blackbox.ui.components.BbTopBar
import com.tdater.blackbox.ui.components.ConfirmDialog
import com.tdater.blackbox.ui.components.Field
import com.tdater.blackbox.ui.components.InfoCard
import com.tdater.blackbox.ui.components.LabeledValue
import com.tdater.blackbox.ui.components.SectionHeader
import com.tdater.blackbox.ui.components.Tag
import com.tdater.blackbox.ui.components.Tone
import com.tdater.blackbox.ui.components.fmtTime
import com.tdater.blackbox.ui.components.outcomeLabel
import com.tdater.blackbox.ui.components.outcomeTone
import com.tdater.blackbox.ui.components.shortId
import kotlinx.coroutines.delay

private val NUMBER = Regex("^\\s*(-?\\d+(?:[.,]\\d+)?)\\s*(.*)$")

/** "2.1 s" -> Measurement(value=2.1, unit="s"). Blank -> not measured (null), never 0. */
fun parseMeasurement(name: String, raw: String): Measurement {
    if (raw.isBlank()) return Measurement(name, null, null)
    val m = NUMBER.find(raw) ?: return Measurement(name, null, raw.trim())
    return Measurement(name, m.groupValues[1].replace(',', '.').toDoubleOrNull(), m.groupValues[2].trim().ifEmpty { null })
}

/**
 * Test run. Plan reference and controls are split by posture: tabletop puts the reference on the
 * upper half and controls on the lower half; wide windows place them side by side.
 */
@Composable
fun RunScreen(vm: MainViewModel, pid: String, runId: String, back: () -> Unit,
              capture: (String, String) -> Unit, openEvidence: (String) -> Unit) {
    val bundle by rememberBundle(vm, pid)
    val b = bundle
    val run = b?.runs?.firstOrNull { it.id == runId }
    val layout = layoutInfo()

    Scaffold(topBar = {
        BbTopBar(run?.planSnapshot?.title ?: "Run", run?.let { "Run ${shortId(it.id)} · plan r${it.planSnapshot.revision}" }, onBack = back)
    }) { pad ->
        if (b == null) return@Scaffold
        if (run == null) { Column(Modifier.padding(pad)) { Missing("Run") }; return@Scaffold }
        val reference: @Composable ColumnScope.() -> Unit = { RunReference(vm, b, run, openEvidence) }
        val controls: @Composable ColumnScope.() -> Unit = { RunControls(vm, b, run, capture) }
        when {
            layout.tabletop -> Column(Modifier.padding(pad).fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp), content = reference)
                HorizontalDivider()
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp), content = controls)
            }
            layout.wide -> Row(Modifier.padding(pad).fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp), content = reference)
                VerticalDivider()
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp), content = controls)
            }
            else -> Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                reference(); HorizontalDivider(Modifier.padding(vertical = 12.dp)); controls()
            }
        }
    }
}

@Composable
private fun ColumnScope.RunReference(vm: MainViewModel, b: ProjectBundle, run: TestRun, openEvidence: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Tag(outcomeLabel(run.outcome), outcomeTone(run.outcome))
        Text("started ${fmtTime(run.startedAt)}" + (run.endedAt?.let { " · ended ${fmtTime(it)}" } ?: ""),
            style = MaterialTheme.typography.labelMedium)
    }
    SectionHeader("Expected result")
    Text(run.planSnapshot.expectedResult, style = MaterialTheme.typography.titleMedium)
    SectionHeader("Steps")
    if (run.planSnapshot.steps.isEmpty()) Text("No steps recorded in this plan revision.")
    val ticked = ProcedureLibrary.tickedSteps(run.markers)
    if (run.planSnapshot.steps.isNotEmpty()) Text("${ticked.count { it in run.planSnapshot.steps.indices }} of ${run.planSnapshot.steps.size} steps ticked",
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    run.planSnapshot.steps.forEachIndexed { i, s ->
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(checked = i in ticked, enabled = !run.isFinished,
                onCheckedChange = { done -> vm.launch { vm.repo.tickStep(run.id, i, s, done) } })
            Text("${i + 1}. $s", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
    if (run.intendedChange.isNotBlank()) { SectionHeader("Intended change"); Text(run.intendedChange) }
    SectionHeader("Build & environment")
    val build = b.builds.firstOrNull { it.id == run.buildReferenceId }
    Text("Build: " + (build?.let { "${it.displayName} · ${it.versionName ?: "version unknown"}" } ?: "not recorded"))
    val env = b.environments.firstOrNull { it.id == run.environmentSnapshotId }
    if (env == null) Text("Environment: not recorded")
    else env.fields.forEach { Text("${it.key}: ${ComparisonEngine.render(it.value)}", style = MaterialTheme.typography.bodySmall) }
    if (run.markers.isNotEmpty()) {
        SectionHeader("Markers")
        run.markers.forEach { Text("${fmtTime(it.at)} — ${it.note}", style = MaterialTheme.typography.bodySmall) }
    }
    val ev = b.evidence.filter { it.testRunId == run.id }
    SectionHeader("Evidence for this run (${ev.size})")
    ev.forEach { e -> EvidenceRow(b, e) { openEvidence(e.id) } }
}

@Composable
private fun ColumnScope.RunControls(vm: MainViewModel, b: ProjectBundle, run: TestRun, capture: (String, String) -> Unit) {
    val finished = run.isFinished
    val plan = b.plans.firstOrNull { it.id == run.testPlanId }
    val measureNames = (plan?.measurements.orEmpty() + run.measurements.map { it.name }).distinct()
    var actual by rememberSaveable(run.id) { mutableStateOf(run.actualResult) }
    var changes by rememberSaveable(run.id) { mutableStateOf(run.actualChanges) }
    var deviations by rememberSaveable(run.id) { mutableStateOf(run.deviations) }
    var notes by rememberSaveable(run.id) { mutableStateOf(run.userNotes) }
    var measures by rememberSaveable(run.id) {
        mutableStateOf(measureNames.map { n -> run.measurements.firstOrNull { it.name == n }?.let { m ->
            (m.value?.let { v -> if (v == Math.rint(v)) v.toLong().toString() else v.toString() } ?: "") + (m.unit?.let { " $it" } ?: "")
        } ?: "" })
    }
    var marker by rememberSaveable(run.id) { mutableStateOf("") }
    var chosen by rememberSaveable(run.id) { mutableStateOf<RunOutcome?>(null) }
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    var confirmAbandon by rememberSaveable { mutableStateOf(false) }
    val inv = b.investigations.firstOrNull { it.id == run.investigationId }

    fun parsed() = measureNames.mapIndexed { i, n -> parseMeasurement(n, measures.getOrElse(i) { "" }) }

    // Autosave the draft so an interrupted run (fold, rotate, process death, call) resumes intact.
    if (!finished) LaunchedEffect(actual, changes, deviations, notes, measures) {
        delay(700)
        vm.repo.updateRunDraft(run.id, actual, changes, deviations, notes, parsed())
    }

    if (finished) {
        SectionHeader("Result")
        LabeledValue("Outcome", run.outcome.name)
        LabeledValue("Actual result", run.actualResult.ifBlank { "not recorded" })
        LabeledValue("Actual changes", run.actualChanges.ifBlank { "not recorded" })
        if (run.deviations.isNotBlank()) LabeledValue("Deviations", run.deviations)
        if (run.userNotes.isNotBlank()) LabeledValue("Notes", run.userNotes)
        run.measurements.forEach { LabeledValue(it.name, it.value?.let { v -> "$v ${it.unit ?: ""}" } ?: "not measured") }
        Text("A PASS means this run met its stated expectation, not that the whole problem is fixed.",
            style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 8.dp))
        if (inv != null && run.outcome != RunOutcome.ABANDONED) {
            val isKnownGood = inv.knownGoodReferenceId == run.id
            OutlinedButton(onClick = { vm.launch { vm.repo.setKnownGood(inv.id, if (isKnownGood) null else run.id) } }) {
                Text(if (isKnownGood) "★ Known-good reference (tap to clear)" else "Mark as known-good reference")
            }
        }
        OutlinedButton(onClick = { capture(run.investigationId, run.id) }) { Text("Attach more evidence") }
        return
    }

    SectionHeader("During the run")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { capture(run.investigationId, run.id) }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Capture evidence") }
    }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Field("Marker note", marker, { marker = it }, Modifier.weight(1f), singleLine = true, placeholder = "e.g. screen off now")
        Button(onClick = { val n = marker; marker = ""; vm.launch { vm.repo.addMarker(run.id, n) } },
            modifier = Modifier.padding(start = 8.dp).heightIn(min = 56.dp)) { Text("Mark") }
    }
    SectionHeader("Record the result")
    Field("Actual result (what happened)", actual, { actual = it }, minLines = 3)
    Field("Actual changes made (if different from intended)", changes, { changes = it })
    Field("Deviations from the steps", deviations, { deviations = it })
    Field("Notes", notes, { notes = it }, minLines = 2)
    measureNames.forEachIndexed { i, n ->
        Field("$n (value and unit; blank = not measured)", measures.getOrElse(i) { "" },
            { v -> measures = measures.toMutableList().also { l -> while (l.size <= i) l.add(""); l[i] = v } }, singleLine = true)
    }
    SectionHeader("Outcome")
    Text("Pick an outcome, then confirm. Nothing is final until you confirm.", style = MaterialTheme.typography.labelSmall)
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
        listOf(RunOutcome.PASS, RunOutcome.FAIL, RunOutcome.PARTIAL, RunOutcome.INCONCLUSIVE).forEach { o ->
            FilterChip(selected = chosen == o, onClick = { chosen = if (chosen == o) null else o },
                label = { Text(outcomeLabel(o), style = MaterialTheme.typography.titleSmall) },
                modifier = Modifier.padding(end = 8.dp).heightIn(min = 48.dp))
        }
    }
    Button(enabled = chosen != null, onClick = { confirmFinish = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(chosen?.let { "Finish run as ${it.name}…" } ?: "Choose an outcome to finish")
    }
    Row {
        TextButton(onClick = { vm.launch { vm.repo.updateRunDraft(run.id, actual, changes, deviations, notes, parsed()); vm.message = "Draft saved. You can leave and resume this run later." } }) {
            Text("Save draft & leave open")
        }
        TextButton(onClick = { confirmAbandon = true }) { Text("Abandon run…", color = MaterialTheme.colorScheme.error) }
    }

    chosen?.let { outcome ->
        if (confirmFinish) ConfirmDialog(
            "Finish as ${outcome.name}?",
            "Expected: ${run.planSnapshot.expectedResult}\n\nActual: ${actual.ifBlank { "(not recorded)" }}\n\nThe run will be closed and its result frozen.",
            "Finish as ${outcome.name}",
            onConfirm = { vm.launch { vm.repo.finishRun(run.id, outcome, actual, changes, deviations, notes, parsed()) } },
            onDismiss = { confirmFinish = false },
        )
    }
    if (confirmAbandon) ConfirmDialog(
        "Abandon this run?", "It will be kept in history as ABANDONED and excluded from results and comparisons.",
        "Abandon", destructive = true,
        onConfirm = { vm.launch { vm.repo.finishRun(run.id, RunOutcome.ABANDONED, actual, changes, deviations, notes, parsed()) } },
        onDismiss = { confirmAbandon = false },
    )
    InfoCard(Modifier.padding(top = 12.dp)) {
        Tag("RUN STAYS OPEN", Tone.INFO)
        Text("Leaving this screen keeps the run open. Its draft is saved automatically.", style = MaterialTheme.typography.bodySmall)
    }
}
