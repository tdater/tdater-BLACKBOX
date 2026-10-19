package com.tdater.blackbox.core.search

import com.tdater.blackbox.core.compare.ComparisonEngine
import com.tdater.blackbox.core.model.ProjectBundle
import com.tdater.blackbox.core.model.RunOutcome

enum class HitKind { PROJECT, INVESTIGATION, EVIDENCE, PLAN, RUN, CLAIM, BUILD, ENVIRONMENT }

data class SearchHit(
    val kind: HitKind,
    val id: String,
    val projectId: String,
    val projectTitle: String,
    val investigationId: String?,
    val title: String,
    val snippet: String,
    val timestamp: Long,
    val withdrawn: Boolean = false,
    val outcome: RunOutcome? = null,
)

data class SearchFilter(
    val query: String = "",
    /** Empty = all kinds. */
    val kinds: Set<HitKind> = emptySet(),
    /** Empty = any outcome. Non-empty limits results to runs with one of these outcomes. */
    val outcomes: Set<RunOutcome> = emptySet(),
    val includeWithdrawn: Boolean = false,
    val includeSamples: Boolean = true,
) {
    val isEmpty: Boolean get() = query.isBlank() && outcomes.isEmpty()
}

/**
 * Plain local text search across every record of the given projects. Every whitespace-separated
 * term must appear (case-insensitive) somewhere in the record's searchable text.
 */
object RecordSearch {
    private const val SNIPPET = 140

    fun search(bundles: List<ProjectBundle>, filter: SearchFilter, limit: Int = 200): List<SearchHit> {
        if (filter.isEmpty) return emptyList()
        val terms = filter.query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val out = mutableListOf<SearchHit>()
        for (b in bundles) {
            if (!filter.includeSamples && b.project.isSample) continue
            val p = b.project
            fun add(kind: HitKind, id: String, iid: String?, title: String, text: String, ts: Long,
                    withdrawn: Boolean = false, outcome: RunOutcome? = null) {
                if (filter.kinds.isNotEmpty() && kind !in filter.kinds) return
                if (filter.outcomes.isNotEmpty() && (outcome == null || outcome !in filter.outcomes)) return
                if (withdrawn && !filter.includeWithdrawn) return
                val hay = "$title\n$text".lowercase()
                if (terms.any { it !in hay }) return
                out += SearchHit(kind, id, p.id, p.title, iid, title, snippet("$title\n$text", terms), ts, withdrawn, outcome)
            }
            add(HitKind.PROJECT, p.id, null, p.title, listOf(p.description, p.objective, p.labels.joinToString(" ")).joinToString("\n"), p.updatedAt)
            b.investigations.forEach {
                add(HitKind.INVESTIGATION, it.id, it.id, it.title,
                    listOf(it.problemStatement, it.desiredOutcome, it.currentQuestion, it.nextAction, it.status.name).joinToString("\n"), it.updatedAt)
            }
            b.evidence.forEach {
                add(HitKind.EVIDENCE, it.id, it.investigationId, it.title,
                    listOf(it.text, it.userSummary, it.labels.joinToString(" "), it.originalFilename.orEmpty(), it.kind.name).joinToString("\n"),
                    it.capturedAt, withdrawn = it.withdrawnAt != null)
            }
            b.plans.forEach {
                add(HitKind.PLAN, it.id, it.investigationId, it.title,
                    (listOf(it.objective, it.prerequisites, it.expectedResult, it.variablesToChange, it.variablesToHoldConstant) + it.steps).joinToString("\n"), it.updatedAt)
            }
            b.runs.forEach {
                add(HitKind.RUN, it.id, it.investigationId, "${it.planSnapshot.title} (${it.outcome.name})",
                    listOf(it.intendedChange, it.actualChanges, it.actualResult, it.deviations, it.userNotes, it.outcome.name,
                        it.markers.joinToString("\n") { m -> m.note }).joinToString("\n"),
                    it.endedAt ?: it.startedAt, outcome = it.outcome)
            }
            b.claims.forEach {
                add(HitKind.CLAIM, it.id, it.investigationId, it.text, "${it.classification.name} ${it.reviewStatus.name}", it.createdAt)
            }
            b.builds.forEach {
                add(HitKind.BUILD, it.id, null, it.displayName,
                    listOfNotNull(it.versionName, it.versionCode?.toString(), it.commitRef, it.provenance).joinToString("\n"), it.createdAt)
            }
            b.environments.forEach { e ->
                add(HitKind.ENVIRONMENT, e.id, null, "Environment snapshot (${e.fields.size} fields)",
                    e.fields.joinToString("\n") { "${it.key}: ${ComparisonEngine.render(it.value)}" }, e.capturedAt)
            }
        }
        return out.sortedByDescending { it.timestamp }.take(limit)
    }

    private fun snippet(text: String, terms: List<String>): String {
        val flat = text.replace(Regex("\\s+"), " ").trim()
        if (terms.isEmpty()) return flat.take(SNIPPET)
        val at = flat.lowercase().indexOf(terms.first()).coerceAtLeast(0)
        val start = (at - SNIPPET / 3).coerceAtLeast(0)
        val end = (start + SNIPPET).coerceAtMost(flat.length)
        return (if (start > 0) "…" else "") + flat.substring(start, end) + (if (end < flat.length) "…" else "")
    }
}
