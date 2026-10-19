package com.tdater.blackbox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tdater.blackbox.core.model.TestPlan
import com.tdater.blackbox.ui.MainViewModel
import com.tdater.blackbox.ui.components.BbTopBar
import com.tdater.blackbox.ui.components.Field

/** Create a plan, or save edits as a new revision. Earlier runs keep the revision they used. */
@Composable
fun PlanEditorScreen(vm: MainViewModel, pid: String, iid: String, planId: String?, back: () -> Unit) {
    val bundle by rememberBundle(vm, pid)
    val existing = planId?.let { id -> bundle?.plans?.firstOrNull { it.id == id } }
    var loaded by rememberSaveable { mutableStateOf(planId == null) }
    var title by rememberSaveable { mutableStateOf("") }
    var objective by rememberSaveable { mutableStateOf("") }
    var prereq by rememberSaveable { mutableStateOf("") }
    var steps by rememberSaveable { mutableStateOf("") }
    var expected by rememberSaveable { mutableStateOf("") }
    var change by rememberSaveable { mutableStateOf("") }
    var hold by rememberSaveable { mutableStateOf("") }
    var measures by rememberSaveable { mutableStateOf("") }
    var regression by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(existing?.id) {
        if (!loaded && existing != null) {
            title = existing.title; objective = existing.objective; prereq = existing.prerequisites
            steps = existing.steps.joinToString("\n"); expected = existing.expectedResult
            change = existing.variablesToChange; hold = existing.variablesToHoldConstant
            measures = existing.measurements.joinToString("\n"); regression = existing.isRegression
            loaded = true
        }
    }

    Scaffold(topBar = {
        BbTopBar(if (existing == null) "New test plan" else "Revise plan (r${existing.revision} → r${existing.revision + 1})", onBack = back)
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp).widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Title", title, { title = it }, singleLine = true, placeholder = "Screen-off for 60 s while connected")
            Field("Objective", objective, { objective = it })
            Field("Prerequisites", prereq, { prereq = it }, minLines = 2)
            Field("Steps (one per line)", steps, { steps = it }, minLines = 4)
            Field("Expected result (required)", expected, { expected = it }, minLines = 2)
            Field("Variables to change", change, { change = it })
            Field("Variables to hold constant", hold, { hold = it })
            Field("Measurements to record (one per line, optional)", measures, { measures = it }, minLines = 2)
            Row(Modifier.fillMaxWidth().clickable { regression = !regression }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = regression, onCheckedChange = { regression = it })
                Text("Use as a regression check (re-run after future fixes)", style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                enabled = title.isNotBlank() && expected.isNotBlank() && loaded,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                onClick = {
                    val plan = TestPlan(
                        id = existing?.id ?: vm.repo.newId(), projectId = pid, investigationId = iid,
                        title = title.trim(), objective = objective, prerequisites = prereq,
                        steps = steps.lines().map { it.trim() }.filter { it.isNotEmpty() },
                        expectedResult = expected.trim(), variablesToChange = change, variablesToHoldConstant = hold,
                        measurements = measures.lines().map { it.trim() }.filter { it.isNotEmpty() },
                        isRegression = regression, createdAt = 0, updatedAt = 0,
                    )
                    vm.launch("Saving plan failed") { vm.repo.savePlan(plan); back() }
                },
            ) { Text(if (existing == null) "Save plan" else "Save as new revision") }
        }
    }
}
