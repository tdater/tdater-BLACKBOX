package com.tdater.blackbox.core.ai

import com.tdater.blackbox.core.compare.ComparisonEngine
import com.tdater.blackbox.core.model.ProjectBundle

/** A record the user can tick for inclusion, with a human label. Nothing is selected by default except by the UI. */
data class ContextCandidate(val label: String, val item: ContextItem, val defaultSelected: Boolean)

/** Turns an investigation's records into reviewable context items. Withdrawn evidence is never offered. */
object AiContext {
    fun candidates(b: ProjectBundle, investigationId: String): List<ContextCandidate> {
        val inv = b.investigations.firstOrNull { it.id == investigationId } ?: return emptyList()
        val pid = b.project.id
        val out = mutableListOf<ContextCandidate>()
        out += ContextCandidate("Investigation: ${inv.title}", ContextItem(inv.id, pid, "investigation", buildString {
            appendLine("Title: ${inv.title}")
            if (inv.problemStatement.isNotBlank()) appendLine("Problem: ${inv.problemStatement}")
            if (inv.desiredOutcome.isNotBlank()) appendLine("Desired outcome: ${inv.desiredOutcome}")
            if (inv.currentQuestion.isNotBlank()) appendLine("Current question: ${inv.currentQuestion}")
            if (inv.nextAction.isNotBlank()) appendLine("Recorded next action: ${inv.nextAction}")
        }.trim()), true)
        b.evidence.filter { it.investigationId == investigationId && it.withdrawnAt == null }
            .sortedByDescending { it.capturedAt }
            .forEach { e ->
                val text = listOf(e.title, e.userSummary, e.text).filter { it.isNotBlank() }.joinToString("\n")
                if (text.isNotBlank()) out += ContextCandidate("Evidence (${e.kind.name.lowercase()}): ${e.title}",
                    ContextItem(e.id, pid, "evidence/${e.kind.name.lowercase()}", text), true)
            }
        b.runs.filter { it.investigationId == investigationId }.sortedByDescending { it.startedAt }.forEach { r ->
            val build = b.builds.firstOrNull { it.id == r.buildReferenceId }
            val env = b.environments.firstOrNull { it.id == r.environmentSnapshotId }
            out += ContextCandidate("Run: ${r.planSnapshot.title} (${r.outcome.name})", ContextItem(r.id, pid, "test_run", buildString {
                appendLine("Plan: ${r.planSnapshot.title} (revision ${r.planSnapshot.revision})")
                appendLine("Expected: ${r.planSnapshot.expectedResult}")
                appendLine("Outcome: ${r.outcome.name}")
                appendLine("Build: ${build?.let { "${it.displayName} ${it.versionName.orEmpty()}" } ?: "not recorded"}")
                env?.fields?.forEach { f -> appendLine("Env ${f.key}: ${ComparisonEngine.render(f.value)}") }
                if (r.intendedChange.isNotBlank()) appendLine("Intended change: ${r.intendedChange}")
                if (r.actualChanges.isNotBlank()) appendLine("Actual changes: ${r.actualChanges}")
                if (r.actualResult.isNotBlank()) appendLine("Actual result: ${r.actualResult}")
                if (r.deviations.isNotBlank()) appendLine("Deviations: ${r.deviations}")
                r.measurements.forEach { m -> appendLine("Measurement ${m.name}: ${m.value ?: "not measured"} ${m.unit.orEmpty()}") }
                r.markers.forEach { m -> appendLine("Marker: ${m.note}") }
                if (r.userNotes.isNotBlank()) appendLine("Notes: ${r.userNotes}")
            }.trim()), true)
        }
        b.claims.filter { it.investigationId == investigationId }.forEach { c ->
            out += ContextCandidate("Claim (${c.classification.name.lowercase()}): ${c.text.take(60)}",
                ContextItem(c.id, pid, "claim", "${c.classification.name} (${c.reviewStatus.name}): ${c.text}"), true)
        }
        return out
    }
}
