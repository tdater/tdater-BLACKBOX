package com.tdater.blackbox.core.procedures

import com.tdater.blackbox.core.model.Procedure
import com.tdater.blackbox.core.model.RunMarker
import com.tdater.blackbox.core.model.TestPlan
import com.tdater.blackbox.core.model.TestRun
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Saved checklists (SOPs): conversions to and from plans, step ticking during a run, and backup encoding. */
object ProcedureLibrary {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    @Serializable
    private data class File(val format: String = FORMAT, val procedures: List<Procedure>)

    const val FORMAT = "blackbox-procedures/1"

    fun fromPlan(plan: TestPlan, templateId: String, id: String, now: Long): Procedure = Procedure(
        id = id, title = plan.title, description = plan.objective, steps = plan.steps, expectedResult = plan.expectedResult,
        templateId = templateId, sourceProjectId = plan.projectId, sourcePlanId = plan.id, createdAt = now, updatedAt = now,
    )

    /** A brand-new plan in the target investigation; the procedure itself is never linked to runs. */
    fun toPlan(p: Procedure, projectId: String, investigationId: String, id: String, now: Long): TestPlan = TestPlan(
        id = id, projectId = projectId, investigationId = investigationId, title = p.title, objective = p.description,
        steps = p.steps, expectedResult = p.expectedResult.ifBlank { "Every step completes as written." },
        createdAt = now, updatedAt = now,
    )

    fun encode(list: List<Procedure>): String = json.encodeToString(File.serializer(), File(procedures = list))
    fun decode(text: String): List<Procedure> = json.decodeFromString(File.serializer(), text).procedures

    // ---- Step ticking. Markers are append-only, so the latest marker for a step decides its state. ----

    fun tickNote(stepIndex: Int, step: String, done: Boolean): String =
        (if (done) "✓ Step ${stepIndex + 1} done: " else "↺ Step ${stepIndex + 1} un-ticked: ") + step.take(200)

    private val markerRe = Regex("""^(✓|↺) Step (\d+) (?:done|un-ticked):""")

    fun tickedSteps(markers: List<RunMarker>): Set<Int> {
        val state = HashMap<Int, Boolean>()
        markers.sortedBy { it.at }.forEach { m ->
            markerRe.find(m.note)?.let { state[it.groupValues[2].toInt() - 1] = it.groupValues[1] == "✓" }
        }
        return state.filterValues { it }.keys
    }

    fun progress(run: TestRun): Pair<Int, Int> =
        tickedSteps(run.markers).count { it in run.planSnapshot.steps.indices } to run.planSnapshot.steps.size
}
