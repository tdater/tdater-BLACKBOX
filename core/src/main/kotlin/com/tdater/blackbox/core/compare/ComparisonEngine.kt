package com.tdater.blackbox.core.compare

import com.tdater.blackbox.core.model.BuildReference
import com.tdater.blackbox.core.model.EnvValue
import com.tdater.blackbox.core.model.EnvironmentSnapshot
import com.tdater.blackbox.core.model.ProjectBundle
import com.tdater.blackbox.core.model.TestRun

/** How two recorded values relate. Deliberately never collapses unknown into unchanged. */
enum class DiffStatus {
    /** Both sides known and different. */
    CHANGED,
    /** Both sides known and equal. */
    UNCHANGED_KNOWN,
    /** At least one side was recorded as explicitly unknown. */
    UNKNOWN,
    /** At least one side has no recorded value at all. */
    MISSING,
    /** Values exist but cannot be compared (e.g. different units). */
    NOT_COMPARABLE,
}

enum class DiffSection { OUTCOME, BUILD, ENVIRONMENT, MEASUREMENT, CHANGE, PLAN }

data class DiffRow(
    val section: DiffSection,
    val key: String,
    val left: String?,
    val right: String?,
    val status: DiffStatus,
    val explanation: String = "",
    /** Record ids the left/right values were read from. */
    val leftSource: String? = null,
    val rightSource: String? = null,
)

data class ComparisonReport(
    val leftRunId: String,
    val rightRunId: String,
    val rows: List<DiffRow>,
    val warnings: List<String>,
    val leftEvidenceIds: List<String>,
    val rightEvidenceIds: List<String>,
) {
    fun rowsWith(status: DiffStatus) = rows.filter { it.status == status }

    companion object {
        const val CORRELATION_NOTE =
            "Recorded differences are observations. A difference that coincides with a different outcome is a correlation, not a demonstrated cause."
    }
}

/**
 * Deterministic run comparison. Uses only recorded data; never invents a baseline,
 * build change, or missing value.
 */
object ComparisonEngine {

    /** Durations differing by more than this ratio trigger a warning. */
    private const val DURATION_WARN_RATIO = 1.5

    fun compareRuns(bundle: ProjectBundle, leftRunId: String, rightRunId: String): ComparisonReport {
        val left = bundle.runs.firstOrNull { it.id == leftRunId }
            ?: throw IllegalArgumentException("Run $leftRunId not found")
        val right = bundle.runs.firstOrNull { it.id == rightRunId }
            ?: throw IllegalArgumentException("Run $rightRunId not found")
        val leftEnv = left.environmentSnapshotId?.let { id -> bundle.environments.firstOrNull { it.id == id } }
        val rightEnv = right.environmentSnapshotId?.let { id -> bundle.environments.firstOrNull { it.id == id } }
        val leftBuild = left.buildReferenceId?.let { id -> bundle.builds.firstOrNull { it.id == id } }
        val rightBuild = right.buildReferenceId?.let { id -> bundle.builds.firstOrNull { it.id == id } }
        return compare(left, right, leftEnv, rightEnv, leftBuild, rightBuild,
            leftEvidence = bundle.evidence.filter { it.testRunId == left.id && it.withdrawnAt == null }.map { it.id },
            rightEvidence = bundle.evidence.filter { it.testRunId == right.id && it.withdrawnAt == null }.map { it.id })
    }

    fun compare(
        left: TestRun,
        right: TestRun,
        leftEnv: EnvironmentSnapshot?,
        rightEnv: EnvironmentSnapshot?,
        leftBuild: BuildReference?,
        rightBuild: BuildReference?,
        leftEvidence: List<String> = emptyList(),
        rightEvidence: List<String> = emptyList(),
    ): ComparisonReport {
        val rows = mutableListOf<DiffRow>()
        val warnings = mutableListOf<String>()

        rows += textRow(DiffSection.OUTCOME, "Outcome", left.outcome.name, right.outcome.name, left.id, right.id)
        rows += textRow(DiffSection.OUTCOME, "Actual result", left.actualResult.ifBlank { null },
            right.actualResult.ifBlank { null }, left.id, right.id)

        // Plan identity
        if (left.testPlanId != right.testPlanId) {
            warnings += "Runs come from different test plans (\"${left.planSnapshot.title}\" vs \"${right.planSnapshot.title}\"); expectations may differ."
        } else if (left.planSnapshot.revision != right.planSnapshot.revision) {
            warnings += "Runs used different revisions of the plan (r${left.planSnapshot.revision} vs r${right.planSnapshot.revision})."
        }
        rows += textRow(DiffSection.PLAN, "Expected result", left.planSnapshot.expectedResult,
            right.planSnapshot.expectedResult, left.testPlanId, right.testPlanId)

        // Build
        rows += buildRows(leftBuild, rightBuild, left, right)

        // Environment
        if (leftEnv == null || rightEnv == null) {
            warnings += "Environment snapshot missing for " + listOfNotNull(
                if (leftEnv == null) "left run" else null,
                if (rightEnv == null) "right run" else null,
            ).joinToString(" and ") + "; configuration differences cannot be established."
        }
        val keys = (leftEnv?.fields.orEmpty().map { it.key } + rightEnv?.fields.orEmpty().map { it.key }).distinct()
        for (key in keys) {
            rows += envRow(key, leftEnv?.valueOf(key), rightEnv?.valueOf(key), leftEnv?.id, rightEnv?.id)
        }
        val unknownCount = rows.count { it.section == DiffSection.ENVIRONMENT && it.status in setOf(DiffStatus.UNKNOWN, DiffStatus.MISSING) }
        if (unknownCount > 0) {
            warnings += "$unknownCount configuration value(s) are unknown or missing on at least one side; treat \"no difference\" there as unestablished."
        }

        // Changes recorded on the runs themselves
        rows += textRow(DiffSection.CHANGE, "Intended change", left.intendedChange.ifBlank { null },
            right.intendedChange.ifBlank { null }, left.id, right.id)
        rows += textRow(DiffSection.CHANGE, "Actual changes", left.actualChanges.ifBlank { null },
            right.actualChanges.ifBlank { null }, left.id, right.id)

        // Measurements
        val mNames = (left.measurements.map { it.name } + right.measurements.map { it.name }).distinct()
        for (name in mNames) {
            val l = left.measurements.firstOrNull { it.name == name }
            val r = right.measurements.firstOrNull { it.name == name }
            rows += when {
                l?.value == null || r?.value == null -> DiffRow(DiffSection.MEASUREMENT, name,
                    l?.let { fmt(it.value, it.unit) }, r?.let { fmt(it.value, it.unit) }, DiffStatus.MISSING,
                    "Not measured in " + listOfNotNull(if (l?.value == null) "left" else null, if (r?.value == null) "right" else null).joinToString(" and ") + " run.",
                    left.id, right.id)
                normUnit(l.unit) != normUnit(r.unit) -> DiffRow(DiffSection.MEASUREMENT, name,
                    fmt(l.value, l.unit), fmt(r.value, r.unit), DiffStatus.NOT_COMPARABLE,
                    "Units differ (${l.unit ?: "none"} vs ${r.unit ?: "none"}); raw numbers are not compared.",
                    left.id, right.id)
                l.value == r.value -> DiffRow(DiffSection.MEASUREMENT, name, fmt(l.value, l.unit),
                    fmt(r.value, r.unit), DiffStatus.UNCHANGED_KNOWN, "", left.id, right.id)
                else -> DiffRow(DiffSection.MEASUREMENT, name, fmt(l.value, l.unit), fmt(r.value, r.unit),
                    DiffStatus.CHANGED, "Δ ${fmtNum(r.value - l.value)}${l.unit?.let { " $it" } ?: ""}", left.id, right.id)
            }
        }

        // Run state & duration
        if (!left.isFinished || !right.isFinished) {
            warnings += "At least one run is not finished; its outcome is not a result."
        }
        val ld = left.durationMs
        val rd = right.durationMs
        if (ld != null && rd != null && ld > 0 && rd > 0) {
            val ratio = maxOf(ld, rd).toDouble() / minOf(ld, rd).toDouble()
            if (ratio >= DURATION_WARN_RATIO) {
                warnings += "Run durations differ substantially (${fmtDuration(ld)} vs ${fmtDuration(rd)}); time-dependent failures may not be comparable."
            }
        }

        return ComparisonReport(left.id, right.id, rows, warnings, leftEvidence, rightEvidence)
    }

    private fun buildRows(lb: BuildReference?, rb: BuildReference?, l: TestRun, r: TestRun): List<DiffRow> {
        if (lb == null && rb == null) {
            return listOf(DiffRow(DiffSection.BUILD, "Build", null, null, DiffStatus.MISSING,
                "No build reference recorded on either run.", l.id, r.id))
        }
        return listOf(
            textRow(DiffSection.BUILD, "Build", lb?.displayName, rb?.displayName, lb?.id, rb?.id),
            textRow(DiffSection.BUILD, "versionName", lb?.versionName, rb?.versionName, lb?.id, rb?.id),
            textRow(DiffSection.BUILD, "versionCode", lb?.versionCode?.toString(), rb?.versionCode?.toString(), lb?.id, rb?.id),
            textRow(DiffSection.BUILD, "Commit / ref", lb?.commitRef, rb?.commitRef, lb?.id, rb?.id),
        )
    }

    private fun textRow(section: DiffSection, key: String, l: String?, r: String?, ls: String?, rs: String?): DiffRow {
        val status = when {
            l == null || r == null -> DiffStatus.MISSING
            l == r -> DiffStatus.UNCHANGED_KNOWN
            else -> DiffStatus.CHANGED
        }
        val why = if (status == DiffStatus.MISSING) "Not recorded on " +
            listOfNotNull(if (l == null) "left" else null, if (r == null) "right" else null).joinToString(" and ") + " side." else ""
        return DiffRow(section, key, l, r, status, why, ls, rs)
    }

    private fun envRow(key: String, l: EnvValue?, r: EnvValue?, ls: String?, rs: String?): DiffRow {
        val lt = l?.let(::render)
        val rt = r?.let(::render)
        return when {
            l == null || r == null -> DiffRow(DiffSection.ENVIRONMENT, key, lt, rt, DiffStatus.MISSING,
                "Key not recorded on " + listOfNotNull(if (l == null) "left" else null, if (r == null) "right" else null).joinToString(" and ") + " side.", ls, rs)
            l is EnvValue.Unknown || r is EnvValue.Unknown -> DiffRow(DiffSection.ENVIRONMENT, key, lt, rt,
                DiffStatus.UNKNOWN, "Recorded as unknown; equality cannot be established.", ls, rs)
            else -> {
                l as EnvValue.Known; r as EnvValue.Known
                when {
                    normUnit(l.unit) != normUnit(r.unit) -> DiffRow(DiffSection.ENVIRONMENT, key, lt, rt,
                        DiffStatus.NOT_COMPARABLE, "Units differ (${l.unit ?: "none"} vs ${r.unit ?: "none"}).", ls, rs)
                    l.text == r.text -> DiffRow(DiffSection.ENVIRONMENT, key, lt, rt, DiffStatus.UNCHANGED_KNOWN, "", ls, rs)
                    else -> DiffRow(DiffSection.ENVIRONMENT, key, lt, rt, DiffStatus.CHANGED, "", ls, rs)
                }
            }
        }
    }

    fun render(v: EnvValue): String = when (v) {
        is EnvValue.Known -> v.text + (v.unit?.let { " $it" } ?: "")
        is EnvValue.Unknown -> "unknown" + (v.note.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
    }

    private fun normUnit(u: String?) = u?.trim()?.lowercase()?.ifEmpty { null }

    private fun fmt(v: Double?, unit: String?) = (v?.let(::fmtNum) ?: "—") + (unit?.let { " $it" } ?: "")

    private fun fmtNum(v: Double): String =
        if (v == Math.rint(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString() else "%.4g".format(v)

    fun fmtDuration(ms: Long): String {
        val s = ms / 1000
        return when {
            s < 60 -> "${s}s"
            s < 3600 -> "${s / 60}m ${s % 60}s"
            else -> "${s / 3600}h ${(s % 3600) / 60}m"
        }
    }
}
