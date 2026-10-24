package com.tdater.blackbox.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tdater.blackbox.core.readings.ReadingSeries
import com.tdater.blackbox.core.readings.Readings
import com.tdater.blackbox.core.templates.ProjectTemplates
import com.tdater.blackbox.ui.MainViewModel
import com.tdater.blackbox.ui.components.BbTopBar
import com.tdater.blackbox.ui.components.Field
import com.tdater.blackbox.ui.components.InfoCard
import com.tdater.blackbox.ui.components.SectionHeader
import com.tdater.blackbox.ui.components.Tag
import com.tdater.blackbox.ui.components.Tone
import com.tdater.blackbox.ui.components.fmtTime
import com.tdater.blackbox.ui.theme.BbColors

/** Numbers over time: log a reading, see each series charted with its range and trend. */
@Composable
fun ReadingsScreen(vm: MainViewModel, pid: String, iid: String?, back: () -> Unit) {
    val bundle by rememberBundle(vm, pid)
    val b = bundle
    Scaffold(topBar = { BbTopBar("Readings", b?.project?.title, onBack = back) }) { pad ->
        if (b == null) return@Scaffold
        val scope = if (iid != null) b.forInvestigation(iid) else b
        val series = remember(scope) { Readings.collect(scope) }
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp).widthIn(max = 900.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val template = ProjectTemplates.byId(b.project.templateId)
            LogReadingCard(vm, pid, iid, suggestions = (series.map { it.name to (it.unit ?: "") } + template.readings).distinctBy { it.first.lowercase() })
            SectionHeader(if (iid != null) "Readings in this ${com.tdater.blackbox.ui.components.LocalLingo.current.investigation.lowercase()}" else "All readings in this project")
            if (series.isEmpty()) Text("No readings yet. Log one above, or add measurements to a test plan and record them during a try.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            series.forEach { SeriesCard(it) }
        }
    }
}

@Composable
fun LogReadingCard(vm: MainViewModel, pid: String, iid: String?, suggestions: List<Pair<String, String>>, runId: String? = null) {
    var name by rememberSaveable { mutableStateOf("") }
    var value by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("") }
    val parsed = Readings.parseValue(value)
    InfoCard {
        Text("Log a reading", style = MaterialTheme.typography.titleMedium)
        if (suggestions.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            suggestions.take(12).forEach { (n, u) ->
                FilterChip(selected = name.equals(n, ignoreCase = true), onClick = { name = n; if (unit.isBlank()) unit = u },
                    label = { Text(if (u.isBlank()) n else "$n ($u)") })
            }
        }
        Field("What did you measure?", name, { name = it }, singleLine = true, placeholder = "Battery voltage")
        Row {
            Field("Value", value, { value = it }, Modifier.weight(2f), singleLine = true, placeholder = "12.4")
            Field("Unit", unit, { unit = it }, Modifier.weight(1f).padding(start = 8.dp), singleLine = true, placeholder = "V")
        }
        if (value.isNotBlank() && parsed == null) Text("That doesn't look like a number yet.", color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelSmall)
        Button(enabled = name.isNotBlank() && parsed != null && !vm.busy, onClick = {
            val n = name; val v = value; val u = unit
            vm.launch("Reading not saved") {
                vm.repo.addReading(pid, iid, runId, n, v, u)
                value = ""
                vm.message = "Saved $n: $v $u".trim()
            }
        }) { Text("Save reading") }
    }
}

@Composable
private fun SeriesCard(s: ReadingSeries) {
    InfoCard {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag("${s.points.size} READING${if (s.points.size == 1) "" else "S"}", Tone.INFO)
            if (s.otherUnits.isNotEmpty()) Tag("MIXED UNITS: ${s.otherUnits.joinToString()}", Tone.WARN)
        }
        Text(s.name, style = MaterialTheme.typography.titleMedium)
        Text("Latest ${Readings.fmt(s.latest.value)}${s.unit?.let { " $it" } ?: ""} · ${fmtTime(s.latest.at)}",
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (s.points.size >= 2) Chart(s)
        Text(Readings.describe(s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Chart(s: ReadingSeries) {
    val line = BbColors.Orange
    val dot = BbColors.Cyan
    val run = BbColors.Violet
    val grid = MaterialTheme.colorScheme.outlineVariant
    val desc = "Chart of ${s.name}: ${s.points.joinToString { Readings.fmt(it.value) }}"
    Canvas(Modifier.fillMaxWidth().height(140.dp).padding(vertical = 6.dp).semantics { contentDescription = desc }) {
        val pts = s.points
        val t0 = pts.first().at.toFloat()
        val t1 = pts.last().at.toFloat().let { if (it == t0) t0 + 1 else it }
        val lo = s.min.let { if (s.max == it) it - 1 else it }
        val hi = s.max.let { if (it == s.min) it + 1 else it }
        val pad = 8.dp.toPx()
        fun x(t: Long) = pad + (t - t0) / (t1 - t0) * (size.width - 2 * pad)
        fun y(v: Double) = (size.height - pad) - ((v - lo) / (hi - lo)).toFloat() * (size.height - 2 * pad)
        for (i in 0..3) {
            val gy = pad + i * (size.height - 2 * pad) / 3
            drawLine(grid, Offset(pad, gy), Offset(size.width - pad, gy), strokeWidth = 1f)
        }
        val path = Path().apply {
            pts.forEachIndexed { i, r -> if (i == 0) moveTo(x(r.at), y(r.value)) else lineTo(x(r.at), y(r.value)) }
        }
        drawPath(path, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        pts.forEach { r -> drawCircle(if (r.fromRun) run else dot, radius = 4.dp.toPx(), center = Offset(x(r.at), y(r.value))) }
    }
}
