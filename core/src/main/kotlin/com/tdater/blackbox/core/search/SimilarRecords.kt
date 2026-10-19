package com.tdater.blackbox.core.search

import com.tdater.blackbox.core.model.ProjectBundle
import kotlin.math.ln

/** A past record that shares distinctive words with what you're looking at now. */
data class SimilarHit(
    val kind: HitKind,
    val id: String,
    val projectId: String,
    val projectTitle: String,
    val investigationId: String?,
    val title: String,
    val snippet: String,
    val timestamp: Long,
    val score: Double,
    /** The shared words that produced the match, rarest first, so the user can judge it. */
    val sharedTerms: List<String>,
)

/**
 * "Have I seen this before?" Deterministic word-overlap matching (weighted so rare words such as
 * error codes count far more than common ones). No AI, no network; it can only find records that
 * actually contain the same distinctive words, and says which words matched.
 */
object SimilarRecords {
    private val STOP = setOf(
        "the", "and", "for", "with", "that", "this", "was", "were", "are", "but", "not", "you", "your", "have", "has", "had",
        "after", "before", "when", "then", "than", "from", "into", "onto", "off", "out", "over", "under", "again", "still",
        "just", "only", "also", "very", "some", "any", "all", "each", "every", "what", "which", "who", "how", "why", "where",
        "there", "here", "they", "them", "their", "its", "it's", "will", "would", "could", "should", "can", "did", "does", "doing",
        "done", "being", "been", "about", "while", "because", "same", "other", "more", "most", "less", "one", "two", "three",
        "note", "photo", "test", "run", "plan", "pass", "fail", "result", "expected", "actual", "observed", "inferred",
        "unknown", "sample", "synthetic", "data", "evidence", "investigation", "project", "time", "now", "today", "yesterday",
    )

    fun tokens(text: String): Set<String> =
        Regex("""[\p{L}\p{N}][\p{L}\p{N}_\-.]*[\p{L}\p{N}]|[\p{L}\p{N}]""").findAll(text.lowercase())
            .map { it.value.trim('.', '-') }
            .filter { it.length >= 3 && it !in STOP }
            .toSet()

    private data class Doc(val hit: SimilarHit, val terms: Set<String>, val text: String)

    /**
     * Finds records across [bundles] that resemble [query]. Records in [excludeInvestigationId] and the ids in
     * [excludeIds] are skipped so the current problem never "matches itself".
     */
    fun find(
        query: String,
        bundles: List<ProjectBundle>,
        excludeInvestigationId: String? = null,
        excludeIds: Set<String> = emptySet(),
        includeSamples: Boolean = false,
        limit: Int = 12,
        minShared: Int = 2,
    ): List<SimilarHit> {
        val q = tokens(query)
        if (q.isEmpty()) return emptyList()
        val docs = mutableListOf<Doc>()
        for (b in bundles) {
            if (b.project.isSample && !includeSamples) continue
            val p = b.project
            fun add(kind: HitKind, id: String, iid: String?, title: String, body: String, ts: Long) {
                if (id in excludeIds || (excludeInvestigationId != null && iid == excludeInvestigationId)) return
                val text = "$title\n$body"
                docs += Doc(SimilarHit(kind, id, p.id, p.title, iid, title, "", ts, 0.0, emptyList()), tokens(text.take(20_000)), text)
            }
            b.investigations.forEach {
                add(HitKind.INVESTIGATION, it.id, it.id, it.title, "${it.problemStatement}\n${it.currentQuestion}\n${it.nextAction}", it.updatedAt)
            }
            b.evidence.filter { it.withdrawnAt == null }.forEach {
                add(HitKind.EVIDENCE, it.id, it.investigationId, it.title, "${it.text.take(8_000)}\n${it.userSummary}\n${it.labels.joinToString(" ")}", it.capturedAt)
            }
            b.claims.forEach { add(HitKind.CLAIM, it.id, it.investigationId, it.text, "", it.createdAt) }
            b.runs.filter { it.isFinished }.forEach {
                add(HitKind.RUN, it.id, it.investigationId, "${it.planSnapshot.title} (${it.outcome.name})",
                    "${it.intendedChange}\n${it.actualResult}\n${it.userNotes}", it.endedAt ?: it.startedAt)
            }
        }
        if (docs.isEmpty()) return emptyList()
        val df = HashMap<String, Int>()
        docs.forEach { d -> d.terms.forEach { if (it in q) df[it] = (df[it] ?: 0) + 1 } }
        val n = docs.size.toDouble()
        fun idf(t: String) = ln(1.0 + n / (df[t] ?: 1))
        return docs.mapNotNull { d ->
            val shared = d.terms.intersect(q)
            if (shared.size < minShared.coerceAtMost(q.size)) return@mapNotNull null
            val score = shared.sumOf { idf(it) } / ln(3.0 + d.terms.size)
            val ordered = shared.sortedByDescending { idf(it) }
            d.hit.copy(score = score, sharedTerms = ordered.take(6), snippet = snippet(d.text, ordered.first()))
        }.sortedByDescending { it.score }.take(limit)
    }

    private fun snippet(text: String, term: String): String {
        val flat = text.replace(Regex("\\s+"), " ").trim()
        val at = flat.lowercase().indexOf(term).coerceAtLeast(0)
        val start = (at - 50).coerceAtLeast(0)
        val end = (start + 140).coerceAtMost(flat.length)
        return (if (start > 0) "…" else "") + flat.substring(start, end) + (if (end < flat.length) "…" else "")
    }
}
