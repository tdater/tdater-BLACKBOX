package com.tdater.blackbox.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tdater.blackbox.core.compare.ComparisonEngine
import com.tdater.blackbox.core.compare.ComparisonReport
import com.tdater.blackbox.core.compare.DiffRow
import com.tdater.blackbox.core.compare.DiffStatus
import com.tdater.blackbox.core.model.RunOutcome
import com.tdater.blackbox.ui.MainViewModel
import com.tdater.blackbox.ui.components.BbTopBar
import com.tdater.blackbox.ui.components.EmptyState
import com.tdater.blackbox.ui.components.InfoCard
import com.tdater.blackbox.ui.components.SectionHeader
import com.tdater.blackbox.ui.components.Tag
import com.tdater.blackbox.ui.components.diffLabel
import com.tdater.blackbox.ui.components.diffTone
import com.tdater.blackbox.ui.components.fmtTime
import com.tdater.blackbox.ui.components.shortId

/** Deterministic comparison of two recorded runs. No AI is involved in building this view. */
@Composable
fun CompareScreen(vm: MainViewModel, pid: String, iid: String, back: () -> Unit) {
    val bundle by rememberBundle(vm, pid)
    val b = bundle
    val runs = b?.runs?.filter { it.investigationId == iid && it.outcome != RunOutcome.ABANDONED }?.sortedBy { it.startedAt }.orEmpty()
    var left by rememberSaveable { mutableStateOf<String?>(null) }
    var right by rememberSaveable { mutableStateOf<String?>(null) }
    var pick by rememberSaveable { mutableStateOf<String?>(null) }
    var showSame by rememberSaveable { mutableStateOf(false) }
    val layout = layoutInfo()

    // Default: known-good (or second-latest) vs most recent. Computed, not written into state.
    val inv = b?.investigations?.firstOrNull { it.id == iid }
    val defaultLeft = if (runs.size >= 2) inv?.knownGoodReferenceId?.takeIf { id -> runs.any { it.id == id } } ?: runs[runs.size - 2].id else null
    val defaultRight = if (runs.size >= 2) runs.last().id.takeIf { it != defaultLeft } ?: runs[runs.size - 2].id else null
    val leftId = left ?: defaultLeft
    val rightId = right ?: defaultRight

    Scaffold(topBar = { BbTopBar("Compare runs", "Recorded differences only", onBack = back) }) { pad ->
        if (b == null) return@Scaffold
        if (runs.size < 2) { EmptyState("Need two runs", "Record at least two runs in this investigation to compare them.", Modifier.padding(pad)); return@Scaffold }
        val report: ComparisonReport? = if (leftId != null && rightId != null && leftId != rightId)
            runCatching { ComparisonEngine.compareRuns(b, leftId, rightId) }.getOrNull() else null
        LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = 12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("left" to leftId, "right" to rightId).forEach { (side, id) ->
                        val r = runs.firstOrNull { it.id == id }
                        OutlinedButton(onClick = { pick = side }, modifier = Modifier.weight(1f)) {
                            Text((if (side == "left") "A: " else "B: ") + (r?.let { "${it.outcome.name} · ${fmtTime(it.startedAt)}" } ?: "choose"), maxLines = 2)
                        }
                    }
                }
                if (leftId == rightId) Text("Choose two different runs.", color = MaterialTheme.colorScheme.error)
            }
            if (report != null) {
                item {
                    if (report.warnings.isNotEmpty()) {
                        SectionHeader("Warnings")
                        report.warnings.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
                    }
                    InfoCard(Modifier.padding(vertical = 8.dp)) {
                        Text(ComparisonReport.CORRELATION_NOTE, style = MaterialTheme.typography.bodySmall)
                    }
                    val counts = DiffStatus.entries.associateWith { s -> report.rows.count { it.status == s } }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        counts.filter { it.value > 0 }.forEach { (s, n) -> Tag("${diffLabel(s)} $n", diffTone(s)) }
                    }
                    FilterChip(selected = showSame, onClick = { showSame = !showSame }, label = { Text("Show unchanged rows") },
                        modifier = Modifier.padding(top = 8.dp))
                }
                val rows = report.rows.filter { showSame || it.status != DiffStatus.UNCHANGED_KNOWN }
                rows.groupBy { it.section }.forEach { (section, list) ->
                    item { SectionHeader(section.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    items(list, key = { "${section.name}:${it.key}" }) { row -> DiffRowView(row, layout.wide) }
                }
                item {
                    SectionHeader("Linked evidence")
                    Text("A: " + report.leftEvidenceIds.joinToString { shortId(it) }.ifEmpty { "none" }, style = MaterialTheme.typography.bodySmall)
                    Text("B: " + report.rightEvidenceIds.joinToString { shortId(it) }.ifEmpty { "none" }, style = MaterialTheme.typography.bodySmall)
                    Text("AI interpretation is not available in this build; nothing above is generated by a model.",
                        style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 16.dp))
                }
            }
        }
    }

    pick?.let { side ->
        ChoiceDialog("Run ${if (side == "left") "A" else "B"}", runs.map { it.id }, if (side == "left") leftId else rightId,
            { id -> runs.first { it.id == id }.let { "${it.outcome.name} · ${it.planSnapshot.title} · ${fmtTime(it.startedAt)} · ${shortId(it.id)}" } },
            { if (side == "left") left = it else right = it }, { pick = null })
    }
}

@Composable
private fun DiffRowView(row: DiffRow, wide: Boolean) {
    InfoCard(Modifier.padding(vertical = 3.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tag(diffLabel(row.status), diffTone(row.status))
            Text(row.key, style = MaterialTheme.typography.titleSmall)
        }
        if (wide) Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { Value("A", row.left, row.leftSource) }
            Column(Modifier.weight(1f)) { Value("B", row.right, row.rightSource) }
        } else {
            Value("A", row.left, row.leftSource)
            Value("B", row.right, row.rightSource)
        }
        if (row.explanation.isNotBlank()) Text(row.explanation, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Value(side: String, v: String?, source: String?) {
    Text("$side: ${v ?: "— not recorded"}" + (source?.let { "  [${shortId(it)}]" } ?: ""), style = MaterialTheme.typography.bodyMedium)
}
