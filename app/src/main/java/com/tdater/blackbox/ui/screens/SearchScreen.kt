package com.tdater.blackbox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tdater.blackbox.core.model.ProjectBundle
import com.tdater.blackbox.core.model.RunOutcome
import com.tdater.blackbox.core.search.HitKind
import com.tdater.blackbox.core.search.RecordSearch
import com.tdater.blackbox.core.search.SearchFilter
import com.tdater.blackbox.core.search.SearchHit
import com.tdater.blackbox.ui.MainViewModel
import com.tdater.blackbox.ui.components.BbTopBar
import com.tdater.blackbox.ui.components.EmptyState
import com.tdater.blackbox.ui.components.Field
import com.tdater.blackbox.ui.components.InfoCard
import com.tdater.blackbox.ui.components.Tag
import com.tdater.blackbox.ui.components.Tone
import com.tdater.blackbox.ui.components.fmtTime
import com.tdater.blackbox.ui.components.outcomeLabel
import com.tdater.blackbox.ui.components.outcomeTone
import kotlinx.coroutines.delay

/** Local search across every project. Nothing leaves the device. */
@Composable
fun SearchScreen(vm: MainViewModel, back: () -> Unit, openHit: (SearchHit) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var kinds by rememberSaveable { mutableStateOf(listOf<String>()) }
    var outcomes by rememberSaveable { mutableStateOf(listOf<String>()) }
    var withdrawn by rememberSaveable { mutableStateOf(false) }
    var samples by rememberSaveable { mutableStateOf(true) }
    var bundles by remember { mutableStateOf<List<ProjectBundle>?>(null) }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }

    LaunchedEffect(Unit) { bundles = vm.repo.loadAllBundles() }
    val filter = SearchFilter(query, kinds.map { HitKind.valueOf(it) }.toSet(), outcomes.map { RunOutcome.valueOf(it) }.toSet(), withdrawn, samples)
    LaunchedEffect(filter, bundles) {
        delay(200)
        hits = bundles?.let { RecordSearch.search(it, filter) }.orEmpty()
    }

    Scaffold(topBar = { BbTopBar("Search", "All projects, on this device only", onBack = back) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 12.dp)) {
            Field("Search notes, logs, plans, runs, claims, builds…", query, { query = it }, singleLine = true)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp)) {
                HitKind.entries.forEach { k ->
                    FilterChip(selected = k.name in kinds, onClick = { kinds = if (k.name in kinds) kinds - k.name else kinds + k.name },
                        label = { Text(k.name.lowercase()) }, modifier = Modifier.padding(end = 6.dp))
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                listOf(RunOutcome.FAIL, RunOutcome.PARTIAL, RunOutcome.INCONCLUSIVE, RunOutcome.PASS, RunOutcome.NOT_FINISHED).forEach { o ->
                    FilterChip(selected = o.name in outcomes, onClick = { outcomes = if (o.name in outcomes) outcomes - o.name else outcomes + o.name },
                        label = { Text(outcomeLabel(o)) }, modifier = Modifier.padding(end = 6.dp))
                }
                FilterChip(selected = withdrawn, onClick = { withdrawn = !withdrawn }, label = { Text("include withdrawn") },
                    modifier = Modifier.padding(end = 6.dp))
                FilterChip(selected = samples, onClick = { samples = !samples }, label = { Text("include sample data") })
            }
            when {
                bundles == null -> EmptyState("Loading…", "Reading local records.")
                filter.isEmpty -> EmptyState("Search your records", "Type words to match, or pick a run outcome such as FAIL to list every failing run. All words must match.")
                hits.isEmpty() -> EmptyState("No matches", "Nothing matches every word with these filters. Withdrawn evidence and sample data can be included with the chips above.")
                else -> {
                    Text("${hits.size} result(s)" + if (hits.size >= 200) " (showing first 200)" else "",
                        style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 6.dp))
                    LazyColumn {
                        items(hits, key = { "${it.kind}:${it.id}" }) { h -> HitRow(h) { openHit(h) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun HitRow(h: SearchHit, onClick: () -> Unit) {
    InfoCard(Modifier.padding(vertical = 3.dp).clickable(onClick = onClick)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag(h.kind.name, Tone.INFO)
            h.outcome?.let { Tag(outcomeLabel(it), outcomeTone(it)) }
            if (h.withdrawn) Tag("WITHDRAWN", Tone.BAD)
        }
        Text(h.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(h.snippet, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text("${h.projectTitle} · ${fmtTime(h.timestamp)}", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
