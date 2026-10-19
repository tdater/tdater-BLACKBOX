package com.tdater.blackbox.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tdater.blackbox.core.model.RunOutcome
import com.tdater.blackbox.core.regression.RegressionChecklistBuilder
import com.tdater.blackbox.core.regression.RegressionStatus
import com.tdater.blackbox.ui.MainViewModel
import com.tdater.blackbox.ui.components.BbTopBar
import com.tdater.blackbox.ui.components.EmptyState
import com.tdater.blackbox.ui.components.InfoCard
import com.tdater.blackbox.ui.components.SectionHeader
import com.tdater.blackbox.ui.components.Tag
import com.tdater.blackbox.ui.components.Tone
import com.tdater.blackbox.ui.components.fmtTime
import com.tdater.blackbox.ui.components.shortId

/**
 * Regression checklist for a project: plans the user confirmed as regression checks, with their
 * status against the newest recorded build, plus fail-then-pass suggestions awaiting confirmation.
 */
@Composable
fun RegressionScreen(vm: MainViewModel, pid: String, back: () -> Unit, openRun: (String) -> Unit) {
    val bundle by rememberBundle(vm, pid)
    var starting by rememberSaveable { mutableStateOf(false) }
    val b = bundle

    Scaffold(topBar = { BbTopBar("Regression checklist", b?.project?.title, onBack = back) }) { pad ->
        if (b == null) return@Scaffold
        val c = RegressionChecklistBuilder.build(b)
        val latestBuild = b.builds.firstOrNull { it.id == c.latestBuildId }
        val latestEnv = b.environments.maxByOrNull { it.capturedAt }
        if (c.items.isEmpty() && c.suggestions.isEmpty()) {
            EmptyState("No regression checks yet",
                "Flag a test plan as a regression check when you revise it, or fix a failure and BLACKBOX will suggest adding its plan here.",
                Modifier.padding(pad))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = 12.dp)) {
            item {
                Text("Newest build: " + (latestBuild?.displayName ?: "none recorded"), style = MaterialTheme.typography.bodyMedium)
                Text("A pass covers only the build it ran on. Runs started here use the newest build and newest environment snapshot.",
                    style = MaterialTheme.typography.labelSmall)
                if (c.items.isNotEmpty()) SectionHeader("Checks (${c.items.count { it.status == RegressionStatus.PASSING }}/${c.items.size} passing)")
            }
            items(c.items, key = { it.plan.id }) { item ->
                val open = b.runs.firstOrNull { it.testPlanId == item.plan.id && it.outcome == RunOutcome.NOT_FINISHED }
                InfoCard(Modifier.padding(vertical = 4.dp)) {
                    Tag(when (item.status) {
                        RegressionStatus.PASSING -> "✓ PASSING"
                        RegressionStatus.FAILING -> "✗ FAILING"
                        RegressionStatus.NEEDS_RERUN -> "↻ NEEDS RE-RUN"
                        RegressionStatus.NEVER_RUN -> "○ NEVER RUN"
                    }, when (item.status) {
                        RegressionStatus.PASSING -> Tone.GOOD
                        RegressionStatus.FAILING -> Tone.BAD
                        RegressionStatus.NEEDS_RERUN -> Tone.WARN
                        RegressionStatus.NEVER_RUN -> Tone.NEUTRAL
                    })
                    Text(item.plan.title, style = MaterialTheme.typography.titleSmall)
                    Text("Expect: ${item.plan.expectedResult}", style = MaterialTheme.typography.bodySmall)
                    Text(item.reason + (item.lastRun?.let { " Last run ${fmtTime(it.endedAt)} · ${shortId(it.id)}" } ?: ""),
                        style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (open != null) Button(onClick = { openRun(open.id) }) { Text("Resume run") }
                        else Button(enabled = !starting, onClick = {
                            starting = true
                            vm.launch {
                                try {
                                    openRun(vm.repo.startRun(item.plan.id, latestBuild?.id, latestEnv?.id, "Regression check"))
                                } finally { starting = false }
                            }
                        }) { Text("Run check") }
                        TextButton(onClick = { vm.launch { vm.repo.setPlanRegression(item.plan.id, false) } }) { Text("Remove") }
                    }
                }
            }
            if (c.suggestions.isNotEmpty()) {
                item {
                    SectionHeader("Suggested from fixed failures")
                    Text("These plans failed and later passed. Add one only if you want it re-checked after future changes.",
                        style = MaterialTheme.typography.labelSmall)
                }
                items(c.suggestions, key = { "s:" + it.plan.id }) { s ->
                    InfoCard(Modifier.padding(vertical = 4.dp)) {
                        Text(s.plan.title, style = MaterialTheme.typography.titleSmall)
                        Text("Failed in run ${shortId(s.failedRunId)}, passed in run ${shortId(s.passedRunId)}.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.launch { vm.repo.setPlanRegression(s.plan.id, true) } }) { Text("Add to checklist") }
                            TextButton(onClick = { openRun(s.failedRunId) }) { Text("View failure") }
                        }
                    }
                }
            }
        }
    }
}
