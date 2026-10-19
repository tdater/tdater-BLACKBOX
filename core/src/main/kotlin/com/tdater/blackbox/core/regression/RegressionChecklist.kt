package com.tdater.blackbox.core.regression

import com.tdater.blackbox.core.model.ProjectBundle
import com.tdater.blackbox.core.model.RunOutcome
import com.tdater.blackbox.core.model.TestPlan
import com.tdater.blackbox.core.model.TestRun

enum class RegressionStatus {
    /** Latest finished run passed on the newest recorded build. */
    PASSING,
    /** Latest finished run did not pass. */
    FAILING,
    /** A newer build exists than the one the latest run used (or the run recorded no build). */
    NEEDS_RERUN,
    NEVER_RUN,
}

data class RegressionItem(
    val plan: TestPlan,
    val status: RegressionStatus,
    val lastRun: TestRun?,
    val reason: String,
)

/** A plan whose recorded history shows a failure later fixed: a candidate the user may confirm. */
data class RegressionSuggestion(val plan: TestPlan, val failedRunId: String, val passedRunId: String)

data class RegressionChecklist(val items: List<RegressionItem>, val suggestions: List<RegressionSuggestion>, val latestBuildId: String?)

/**
 * The checklist only contains plans the user explicitly flagged as regression checks.
 * Fail-then-pass history only produces suggestions; nothing is added automatically.
 */
object RegressionChecklistBuilder {

    fun build(b: ProjectBundle): RegressionChecklist {
        val latestBuild = b.builds.maxByOrNull { it.createdAt }
        val finishedByPlan = b.runs
            .filter { it.isFinished && it.outcome != RunOutcome.ABANDONED }
            .groupBy { it.testPlanId }
            .mapValues { (_, v) -> v.sortedBy { it.endedAt ?: it.startedAt } }

        val items = b.plans.filter { it.isRegression }.map { plan ->
            val runs = finishedByPlan[plan.id].orEmpty()
            val last = runs.lastOrNull()
            when {
                last == null -> RegressionItem(plan, RegressionStatus.NEVER_RUN, null, "Never run.")
                last.outcome != RunOutcome.PASS ->
                    RegressionItem(plan, RegressionStatus.FAILING, last, "Last run was ${last.outcome.name}.")
                latestBuild != null && last.buildReferenceId != latestBuild.id ->
                    RegressionItem(plan, RegressionStatus.NEEDS_RERUN, last,
                        if (last.buildReferenceId == null) "Last pass recorded no build; newest build is \"${latestBuild.displayName}\"."
                        else "Last pass was on an older build; newest is \"${latestBuild.displayName}\".")
                else -> RegressionItem(plan, RegressionStatus.PASSING, last,
                    if (latestBuild == null) "Passed (no builds recorded to check against)." else "Passed on \"${latestBuild.displayName}\".")
            }
        }.sortedBy { it.status.ordinal }

        val suggestions = b.plans.filter { !it.isRegression }.mapNotNull { plan ->
            val runs = finishedByPlan[plan.id].orEmpty()
            val fail = runs.firstOrNull { it.outcome == RunOutcome.FAIL } ?: return@mapNotNull null
            val laterPass = runs.firstOrNull { it.outcome == RunOutcome.PASS && (it.endedAt ?: it.startedAt) > (fail.endedAt ?: fail.startedAt) }
                ?: return@mapNotNull null
            RegressionSuggestion(plan, fail.id, laterPass.id)
        }
        return RegressionChecklist(items, suggestions, latestBuild?.id)
    }
}
