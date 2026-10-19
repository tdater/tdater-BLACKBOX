package com.tdater.blackbox.core.resume

import com.tdater.blackbox.core.model.GenerationMode
import com.tdater.blackbox.core.model.Investigation
import com.tdater.blackbox.core.model.ProjectBundle
import com.tdater.blackbox.core.model.ResumeCapsule
import com.tdater.blackbox.core.model.RunOutcome
import com.tdater.blackbox.core.model.SourceRef
import com.tdater.blackbox.core.model.TestRun

enum class Freshness { CURRENT, STALE }

/** Builds resume capsules from structured records only. Works fully offline. */
object ResumeGenerator {

    const val NO_KNOWN_GOOD = "No known-good run recorded."
    const val NO_NEXT_ACTION = "No next action recorded."
    const val NO_FAILURE = "No failing or partial run recorded."
    const val NO_TESTED_CHANGE = "No finished run with a recorded change."

    fun generate(bundle: ProjectBundle, investigationId: String, id: String, now: Long): ResumeCapsule {
        val inv = bundle.investigations.firstOrNull { it.id == investigationId }
            ?: throw IllegalArgumentException("Investigation $investigationId not found")
        val runs = bundle.runs.filter { it.investigationId == investigationId }
        val finished = runs.filter { it.isFinished && it.outcome != RunOutcome.ABANDONED }
            .sortedByDescending { it.endedAt ?: it.startedAt }
        val refs = mutableListOf<SourceRef>()
        refs += SourceRef("investigation", inv.id, inv.title)

        val knownGood = knownGoodText(inv, runs, finished, refs)

        val failure = finished.firstOrNull { it.outcome == RunOutcome.FAIL || it.outcome == RunOutcome.PARTIAL }
        val failureText = if (failure != null) {
            refs += runRef(failure)
            "${failure.outcome.name}: ${failure.planSnapshot.title} — " +
                failure.actualResult.ifBlank { "(actual result not recorded)" } +
                " [run ${short(failure.id)}]"
        } else if (inv.problemStatement.isNotBlank()) {
            "Reported problem (not yet reproduced in a recorded run): ${inv.problemStatement}"
        } else NO_FAILURE

        val lastChanged = finished.firstOrNull { it.intendedChange.isNotBlank() || it.actualChanges.isNotBlank() }
        val changeText = if (lastChanged != null) {
            refs += runRef(lastChanged)
            val change = lastChanged.actualChanges.ifBlank { lastChanged.intendedChange + " (intended; actual change not recorded)" }
            "$change → ${lastChanged.outcome.name} [run ${short(lastChanged.id)}]"
        } else NO_TESTED_CHANGE

        val open = runs.firstOrNull { it.outcome == RunOutcome.NOT_FINISHED }
        val next = buildString {
            append(inv.nextAction.ifBlank { NO_NEXT_ACTION })
            if (open != null) {
                refs += runRef(open)
                append(" Unfinished run in progress: ${open.planSnapshot.title} [run ${short(open.id)}].")
            }
        }

        bundle.evidence
            .filter { it.investigationId == investigationId && it.withdrawnAt == null }
            .sortedByDescending { it.capturedAt }
            .take(5)
            .forEach { refs += SourceRef("evidence", it.id, it.title) }

        val objective = listOf(inv.desiredOutcome, bundle.project.objective, inv.problemStatement)
            .firstOrNull { it.isNotBlank() } ?: "No objective recorded."

        return ResumeCapsule(
            id = id,
            projectId = inv.projectId,
            investigationId = inv.id,
            sourceRevision = inv.revision,
            generatedAt = now,
            objective = objective,
            knownGoodState = knownGood,
            currentFailure = failureText,
            lastTestedChange = changeText,
            nextAction = next,
            references = refs.distinctBy { it.kind to it.id },
            generationMode = GenerationMode.DETERMINISTIC,
        )
    }

    private fun knownGoodText(inv: Investigation, all: List<TestRun>, finished: List<TestRun>, refs: MutableList<SourceRef>): String {
        val marked = inv.knownGoodReferenceId?.let { id -> all.firstOrNull { it.id == id } }
        if (marked != null) {
            refs += runRef(marked)
            return "Known-good: ${marked.planSnapshot.title} → ${marked.outcome.name} [run ${short(marked.id)}]"
        }
        if (inv.knownGoodReferenceId != null) {
            return "Known-good reference ${short(inv.knownGoodReferenceId)} no longer exists. $NO_KNOWN_GOOD"
        }
        val pass = finished.firstOrNull { it.outcome == RunOutcome.PASS }
        return if (pass != null) {
            refs += runRef(pass)
            "$NO_KNOWN_GOOD Latest passing run (not marked known-good): ${pass.planSnapshot.title} [run ${short(pass.id)}]"
        } else NO_KNOWN_GOOD
    }

    fun freshness(capsule: ResumeCapsule, investigation: Investigation): Freshness =
        if (investigation.revision == capsule.sourceRevision) Freshness.CURRENT else Freshness.STALE

    private fun runRef(r: TestRun) = SourceRef("run", r.id, "${r.planSnapshot.title} (${r.outcome.name})")

    fun short(id: String) = id.take(8)
}
