package com.tdater.blackbox.core.report

import com.tdater.blackbox.core.compare.ComparisonEngine
import com.tdater.blackbox.core.model.ClaimClassification
import com.tdater.blackbox.core.model.EvidenceKind
import com.tdater.blackbox.core.model.ProjectBundle
import com.tdater.blackbox.core.model.ReviewStatus
import com.tdater.blackbox.core.model.RunOutcome
import com.tdater.blackbox.core.readings.Readings
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A printable summary of one investigation. Plain structure; the app decides how to draw it (PDF). */
data class Report(val title: String, val subtitle: String, val sections: List<ReportSection>)

data class ReportSection(
    val heading: String,
    val paragraphs: List<String> = emptyList(),
    val bullets: List<String> = emptyList(),
    /** Photo/image evidence ids to embed under this section, in order. */
    val imageEvidenceIds: List<String> = emptyList(),
)

/**
 * Deterministic report from recorded data only. Nothing is summarized by AI; missing things say "not recorded".
 * Withdrawn evidence is listed as withdrawn, never silently dropped.
 */
object ReportBuilder {
    fun build(b: ProjectBundle, investigationId: String, zone: ZoneId = ZoneId.systemDefault(),
              includePhotos: Boolean = true, maxPhotos: Int = 12): Report {
        val inv = b.investigations.first { it.id == investigationId }
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone)
        fun t(ms: Long?) = ms?.let { fmt.format(Instant.ofEpochMilli(it)) } ?: "unknown"
        val ev = b.evidence.filter { it.investigationId == inv.id }.sortedBy { it.capturedAt }
        val runs = b.runs.filter { it.investigationId == inv.id }.sortedBy { it.startedAt }
        val claims = b.claims.filter { it.investigationId == inv.id }
        val sections = mutableListOf<ReportSection>()

        sections += ReportSection("Summary", bullets = listOf(
            "Status: ${inv.status.name.lowercase()}",
            "Problem: ${inv.problemStatement.ifBlank { "not recorded" }}",
            "Goal: ${inv.desiredOutcome.ifBlank { "not recorded" }}",
            "Current question: ${inv.currentQuestion.ifBlank { "not recorded" }}",
            "Next step: ${inv.nextAction.ifBlank { "not recorded" }}",
            "Records: ${ev.count { it.withdrawnAt == null }} evidence, ${runs.size} tests, ${claims.size} findings",
        ))

        val known = inv.knownGoodReferenceId?.let { id -> runs.firstOrNull { it.id == id } }
        if (known != null) sections += ReportSection("Last known good",
            paragraphs = listOf("${known.planSnapshot.title}: ${known.outcome.name} on ${t(known.endedAt ?: known.startedAt)}. " +
                known.actualResult.ifBlank { "" }))

        if (runs.isNotEmpty()) sections += ReportSection("Tests", bullets = runs.map { r ->
            val env = b.environments.firstOrNull { it.id == r.environmentSnapshotId }
            buildString {
                append("${t(r.startedAt)} · ${r.planSnapshot.title} · ${if (r.outcome == RunOutcome.NOT_FINISHED) "in progress" else r.outcome.name}")
                if (r.intendedChange.isNotBlank()) append(" · changed: ${r.intendedChange}")
                if (r.actualResult.isNotBlank()) append(" · result: ${r.actualResult}")
                if (r.measurements.isNotEmpty()) append(" · " + r.measurements.joinToString { m ->
                    "${m.name} ${m.value?.let(Readings::fmt) ?: "not measured"}${m.unit?.let { " $it" } ?: ""}" })
                if (env != null && env.fields.isNotEmpty()) append(" · conditions: " +
                    env.fields.take(6).joinToString { "${it.key}=${ComparisonEngine.render(it.value)}" })
            }
        })

        if (claims.isNotEmpty()) sections += ReportSection("Findings", bullets = claims.sortedBy { it.createdAt }.map { c ->
            val label = when (c.classification) {
                ClaimClassification.OBSERVED -> "Observed"
                ClaimClassification.INFERRED -> "Hypothesis"
                ClaimClassification.SUPPORTED_RESULT -> "Supported by a test"
                ClaimClassification.UNKNOWN -> "Unknown"
            }
            val review = when (c.reviewStatus) { ReviewStatus.ACCEPTED -> ", accepted"; ReviewStatus.REJECTED -> ", rejected"; else -> "" }
            "[$label$review] ${c.text}" + if (c.author != "user") " (suggested by ${c.author})" else ""
        })

        val series = Readings.collect(b.forInvestigation(inv.id))
        if (series.isNotEmpty()) sections += ReportSection("Readings", bullets = series.map { "${it.name}: ${Readings.describe(it)}" })

        if (ev.isNotEmpty()) {
            val photos = if (includePhotos) ev.filter {
                it.withdrawnAt == null && it.attachmentPath != null && (it.kind == EvidenceKind.PHOTO || it.kind == EvidenceKind.IMAGE_IMPORT)
            }.take(maxPhotos).map { it.id } else emptyList()
            sections += ReportSection("Evidence", bullets = ev.map { e ->
                val body = e.text.lineSequence().firstOrNull()?.take(160)?.takeIf { it.isNotBlank() && it != e.title }
                "${t(e.capturedAt)} · ${e.kind.name.lowercase()} · ${e.title}" +
                    (body?.let { " — $it" } ?: "") + (if (e.withdrawnAt != null) " (withdrawn)" else "")
            }, imageEvidenceIds = photos)
        }

        return Report(
            title = inv.title,
            subtitle = "${b.project.title} · generated ${t(System.currentTimeMillis())} by BLACKBOX from recorded data" +
                if (b.project.isSample) " · SAMPLE (synthetic)" else "",
            sections = sections,
        )
    }

    /** Same content as Markdown, for sharing as text. */
    fun markdown(r: Report): String = buildString {
        appendLine("# ${r.title}"); appendLine("_${r.subtitle}_"); appendLine()
        r.sections.forEach { s ->
            appendLine("## ${s.heading}")
            s.paragraphs.forEach { appendLine(it); appendLine() }
            s.bullets.forEach { appendLine("- $it") }
            appendLine()
        }
    }
}
