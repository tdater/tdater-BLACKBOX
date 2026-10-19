package com.tdater.blackbox.core.handoff

import com.tdater.blackbox.core.compare.ComparisonEngine
import com.tdater.blackbox.core.model.ClaimClassification
import com.tdater.blackbox.core.model.EnvValue
import com.tdater.blackbox.core.model.Evidence
import com.tdater.blackbox.core.model.ProjectBundle
import com.tdater.blackbox.core.model.ReviewStatus
import com.tdater.blackbox.core.model.RunOutcome
import com.tdater.blackbox.core.model.TestRun
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class HandoffOptions(
    val audience: String = "Developer",
    /** Evidence explicitly selected for inclusion. Anything else is excluded. */
    val includedEvidenceIds: Set<String>,
    val expanded: Boolean = false,
    /** Maximum characters of inline text/log excerpt per evidence item. */
    val excerptLimit: Int = 1500,
    val zone: ZoneId = ZoneId.systemDefault(),
)

data class HandoffResult(
    val markdown: String,
    val sourceRecordIds: List<String>,
    val includedEvidenceIds: List<String>,
    val excludedEvidenceIds: List<String>,
)

/**
 * Generates a structured fix-request in Markdown from local records only.
 * Excluded evidence never appears in the output, not even by title.
 */
object HandoffGenerator {

    const val ATTACHMENT_NOTICE =
        "Attachments are listed by reference only. This text does not transport the files; send them separately or export a project archive."

    fun generate(bundle: ProjectBundle, investigationId: String, opts: HandoffOptions): HandoffResult {
        val inv = bundle.investigations.firstOrNull { it.id == investigationId }
            ?: throw IllegalArgumentException("Investigation $investigationId not found")
        val zone = opts.zone
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(zone)
        fun t(ms: Long?) = ms?.let { fmt.format(Instant.ofEpochMilli(it)) } ?: "unknown time"

        val allEvidence = bundle.evidence.filter { it.investigationId == investigationId && it.withdrawnAt == null }
        val included = allEvidence.filter { it.id in opts.includedEvidenceIds }
        val excluded = allEvidence.filter { it.id !in opts.includedEvidenceIds }
        val includedIds = included.map { it.id }.toSet()
        val runs = bundle.runs.filter { it.investigationId == investigationId && it.outcome != RunOutcome.ABANDONED }
            .sortedBy { it.startedAt }
        val plans = bundle.plans.filter { it.investigationId == investigationId }
        val sources = linkedSetOf(inv.id)

        val md = StringBuilder()
        md.appendLine("# Fix request: ${inv.title}")
        md.appendLine()
        md.appendLine("_Audience: ${opts.audience} · Generated ${t(System.currentTimeMillis())} by BLACKBOX from local records (deterministic, no AI). Record IDs in brackets._")
        md.appendLine()

        md.appendLine("## 1. Goal and problem")
        md.appendLine("- Problem: ${inv.problemStatement.ifBlank { "Not recorded." }} [${short(inv.id)}]")
        md.appendLine("- Desired outcome: ${inv.desiredOutcome.ifBlank { "Not recorded." }}")
        if (inv.currentQuestion.isNotBlank()) md.appendLine("- Current question: ${inv.currentQuestion}")
        md.appendLine()

        md.appendLine("## 2. Environment and build")
        val runEnvIds = runs.mapNotNull { it.environmentSnapshotId }.distinct()
        val runBuildIds = runs.mapNotNull { it.buildReferenceId }.distinct()
        if (runBuildIds.isEmpty()) md.appendLine("- Build: not recorded on any run.")
        bundle.builds.filter { it.id in runBuildIds }.forEach { b ->
            sources += b.id
            md.appendLine("- Build **${b.displayName}**: versionName=${b.versionName ?: "unknown"}, versionCode=${b.versionCode ?: "unknown"}, ref=${b.commitRef ?: "unknown"} (source: ${b.provenance}) [${short(b.id)}]")
        }
        if (runEnvIds.isEmpty()) md.appendLine("- Environment: no snapshot recorded on any run.")
        bundle.environments.filter { it.id in runEnvIds }.let { envs ->
            val shown = if (opts.expanded) envs else envs.takeLast(1)
            shown.forEach { e ->
                sources += e.id
                md.appendLine("- Environment snapshot ${t(e.capturedAt)} [${short(e.id)}]:")
                e.fields.forEach { f -> md.appendLine("  - ${f.key}: ${ComparisonEngine.render(f.value)}") }
            }
            if (!opts.expanded && envs.size > 1) md.appendLine("- (${envs.size - 1} earlier snapshot(s) omitted; use expanded detail.)")
        }
        md.appendLine()

        md.appendLine("## 3. Reproduction steps")
        val primaryPlan = runs.lastOrNull { it.outcome == RunOutcome.FAIL || it.outcome == RunOutcome.PARTIAL }
            ?.let { r -> plans.firstOrNull { it.id == r.testPlanId } } ?: plans.firstOrNull()
        if (primaryPlan == null) md.appendLine("No test plan recorded.") else {
            sources += primaryPlan.id
            md.appendLine("From plan \"${primaryPlan.title}\" r${primaryPlan.revision} [${short(primaryPlan.id)}]:")
            if (primaryPlan.prerequisites.isNotBlank()) md.appendLine("Prerequisites: ${primaryPlan.prerequisites}")
            if (primaryPlan.steps.isEmpty()) md.appendLine("(No steps recorded.)")
            primaryPlan.steps.forEachIndexed { i, s -> md.appendLine("${i + 1}. $s") }
        }
        md.appendLine()

        md.appendLine("## 4. Expected behavior")
        md.appendLine(primaryPlan?.expectedResult?.ifBlank { null } ?: inv.desiredOutcome.ifBlank { "Not recorded." })
        md.appendLine()

        md.appendLine("## 5. Observed behavior")
        val failing = runs.filter { it.outcome == RunOutcome.FAIL || it.outcome == RunOutcome.PARTIAL }
        if (failing.isEmpty()) md.appendLine("No failing or partial run recorded.")
        (if (opts.expanded) failing else failing.takeLast(3)).forEach { r ->
            sources += r.id
            md.appendLine("- ${r.outcome.name} ${t(r.endedAt)}: ${r.actualResult.ifBlank { "(actual result not recorded)" }} [run ${short(r.id)}]")
        }
        bundle.claims.filter { it.investigationId == investigationId && it.reviewStatus != ReviewStatus.REJECTED &&
            it.classification == ClaimClassification.OBSERVED }.forEach { c ->
            sources += c.id
            md.appendLine("- Observed: ${c.text} [claim ${short(c.id)}]${refList(c.evidenceIds, includedIds)}")
        }
        md.appendLine()

        md.appendLine("## 6. Changes already tested")
        val changed = runs.filter { it.isFinished && (it.intendedChange.isNotBlank() || it.actualChanges.isNotBlank()) }
        if (changed.isEmpty()) md.appendLine("No tested changes recorded.")
        changed.forEach { r ->
            sources += r.id
            md.appendLine("- ${r.actualChanges.ifBlank { r.intendedChange + " (intended)" }} → ${r.outcome.name} [run ${short(r.id)}]")
        }
        md.appendLine()

        md.appendLine("## 7. Outcomes and regressions")
        if (runs.isEmpty()) md.appendLine("No runs recorded.")
        md.appendLine(runSummary(runs))
        plans.filter { it.isRegression }.forEach { p ->
            val last = runs.lastOrNull { it.testPlanId == p.id && it.isFinished }
            md.appendLine("- Regression check \"${p.title}\": ${last?.outcome?.name ?: "never run"}")
        }
        md.appendLine("_A PASS means the stated expectation passed in that run, not that the whole problem is fixed._")
        md.appendLine()

        md.appendLine("## 8. Selected artifacts")
        md.appendLine("_${ATTACHMENT_NOTICE}_")
        if (included.isEmpty()) md.appendLine("No evidence selected.")
        included.forEach { e ->
            sources += e.id
            md.appendLine(evidenceLine(e, t(e.capturedAt)))
            val body = e.text
            if (body.isNotBlank()) {
                val lim = if (opts.expanded) opts.excerptLimit * 4 else opts.excerptLimit
                val cut = body.length > lim
                md.appendLine("```")
                md.appendLine(if (cut) body.take(lim) else body)
                md.appendLine("```")
                if (cut) md.appendLine("_Excerpt truncated: showing ${lim} of ${body.length} characters. Full text is in record ${short(e.id)}._")
            }
        }
        if (excluded.isNotEmpty()) md.appendLine("_${excluded.size} evidence item(s) were deliberately excluded._")
        md.appendLine()

        md.appendLine("## 9. Unknowns and conflicts")
        val unknowns = mutableListOf<String>()
        bundle.environments.filter { it.id in runEnvIds }.forEach { e ->
            e.fields.filter { it.value is EnvValue.Unknown }.forEach { unknowns += "Environment \"${it.key}\" recorded as unknown [${short(e.id)}]" }
        }
        runs.filter { !it.isFinished }.forEach { unknowns += "Run ${short(it.id)} (${it.planSnapshot.title}) is unfinished; no result." }
        runs.filter { it.outcome == RunOutcome.INCONCLUSIVE }.forEach { unknowns += "Run ${short(it.id)} was inconclusive." }
        runs.filter { it.buildReferenceId == null && it.isFinished }.forEach { unknowns += "Run ${short(it.id)} has no build reference." }
        bundle.claims.filter { it.investigationId == investigationId && it.reviewStatus != ReviewStatus.REJECTED &&
            it.classification == ClaimClassification.UNKNOWN }.forEach { unknowns += "${it.text} [claim ${short(it.id)}]" }
        bundle.claims.filter { it.investigationId == investigationId && it.reviewStatus != ReviewStatus.REJECTED &&
            it.classification == ClaimClassification.INFERRED }.forEach { unknowns += "Hypothesis (unverified): ${it.text} [claim ${short(it.id)}]" }
        conflictingOutcomes(runs).forEach { unknowns += it }
        if (unknowns.isEmpty()) md.appendLine("None recorded. (This does not mean none exist.)")
        unknowns.forEach { md.appendLine("- $it") }
        md.appendLine()

        md.appendLine("## 10. Requested next action and constraints")
        md.appendLine(inv.nextAction.ifBlank { "No next action recorded." })
        md.appendLine()
        md.appendLine("Constraints: do not assume causation from correlation; ask for missing values instead of guessing.")

        return HandoffResult(md.toString(), sources.toList(), included.map { it.id }, excluded.map { it.id })
    }

    private fun evidenceLine(e: Evidence, time: String): String {
        val att = if (e.attachmentPath != null)
            " — attachment `${e.originalFilename ?: "file"}` (${e.mimeType ?: "unknown type"}, ${e.attachmentBytes ?: "?"} bytes, sha256 ${e.attachmentSha256?.take(12) ?: "n/a"}…) **not included in this text**"
        else ""
        val summary = e.userSummary.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
        return "- ${e.kind.name} \"${e.title}\" captured $time$summary$att [${short(e.id)}]"
    }

    private fun refList(ids: List<String>, included: Set<String>): String {
        val shown = ids.filter { it in included }
        val hidden = ids.size - shown.size
        if (ids.isEmpty()) return ""
        return " (evidence: " + (shown.map(::short) + if (hidden > 0) listOf("$hidden excluded") else emptyList()).joinToString(", ") + ")"
    }

    private fun runSummary(runs: List<TestRun>): String =
        runs.groupBy { it.outcome }.entries.joinToString("; ") { "${it.key.name}: ${it.value.size}" }
            .let { if (it.isBlank()) "" else "- Totals: $it" }

    /** Same plan revision, same build+environment ids, different finished outcomes. */
    private fun conflictingOutcomes(runs: List<TestRun>): List<String> =
        runs.filter { it.isFinished && it.outcome != RunOutcome.INCONCLUSIVE }
            .groupBy { Triple(it.testPlanId to it.planSnapshot.revision, it.buildReferenceId, it.environmentSnapshotId) }
            .filter { (k, v) -> k.second != null && k.third != null && v.map { it.outcome }.distinct().size > 1 }
            .map { (_, v) -> "Conflicting outcomes under the same recorded plan/build/environment: " +
                v.joinToString(", ") { "${short(it.id)}=${it.outcome.name}" } + ". Possible unrecorded variable or intermittent failure." }

    fun short(id: String) = id.take(8)
}

/** Heuristic sensitive-content hints. Not exhaustive and never presented as guaranteed redaction. */
object SensitiveScanner {
    private val patterns = listOf(
        "email address" to Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
        "IPv4 address" to Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"),
        "possible API key/token" to Regex("(?i)\\b(?:sk|pk|api|key|token|bearer)[-_ ]?[A-Za-z0-9]{16,}"),
        "possible password field" to Regex("(?i)password\\s*[:=]"),
        "MAC address" to Regex("\\b(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}\\b"),
        "phone-like number" to Regex("\\b\\+?\\d[\\d -]{8,}\\d\\b"),
    )

    fun scan(text: String): List<String> = patterns.mapNotNull { (label, re) ->
        val n = re.findAll(text).count()
        if (n > 0) "$n × $label" else null
    }
}
