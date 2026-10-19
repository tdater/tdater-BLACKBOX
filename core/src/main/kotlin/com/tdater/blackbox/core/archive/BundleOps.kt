package com.tdater.blackbox.core.archive

import com.tdater.blackbox.core.model.Claim
import com.tdater.blackbox.core.model.ProjectBundle

/** Structural consistency checks applied to imported bundles. */
object BundleValidator {
    fun validate(b: ProjectBundle): List<String> {
        val p = mutableListOf<String>()
        val pid = b.project.id
        fun own(kind: String, id: String, projectId: String) {
            if (projectId != pid) p += "$kind $id belongs to another project"
        }
        b.investigations.forEach { own("investigation", it.id, it.projectId) }
        b.sessions.forEach { own("session", it.id, it.projectId) }
        b.builds.forEach { own("build", it.id, it.projectId) }
        b.environments.forEach { own("environment", it.id, it.projectId) }
        b.evidence.forEach { own("evidence", it.id, it.projectId) }
        b.claims.forEach { own("claim", it.id, it.projectId) }
        b.plans.forEach { own("plan", it.id, it.projectId) }
        b.runs.forEach { own("run", it.id, it.projectId) }
        b.timeline.forEach { own("timeline", it.id, it.projectId) }
        b.capsules.forEach { own("capsule", it.id, it.projectId) }
        b.handoffs.forEach { own("handoff", it.id, it.projectId) }

        val allIds = allIds(b)
        if (allIds.size != allIds.toSet().size) p += "duplicate record ids"
        val inv = b.investigations.map { it.id }.toSet()
        val plans = b.plans.map { it.id }.toSet()
        val runs = b.runs.map { it.id }.toSet()
        val envs = b.environments.map { it.id }.toSet()
        val builds = b.builds.map { it.id }.toSet()
        val sessions = b.sessions.map { it.id }.toSet()
        b.plans.filter { it.investigationId !in inv }.forEach { p += "plan ${it.id} references missing investigation" }
        b.runs.forEach { r ->
            if (r.investigationId !in inv) p += "run ${r.id} references missing investigation"
            if (r.testPlanId !in plans) p += "run ${r.id} references missing plan"
            if (r.environmentSnapshotId != null && r.environmentSnapshotId !in envs) p += "run ${r.id} references missing environment"
            if (r.buildReferenceId != null && r.buildReferenceId !in builds) p += "run ${r.id} references missing build"
            if (r.sessionId != null && r.sessionId !in sessions) p += "run ${r.id} references missing session"
        }
        b.evidence.forEach { e ->
            if (e.investigationId != null && e.investigationId !in inv) p += "evidence ${e.id} references missing investigation"
            if (e.testRunId != null && e.testRunId !in runs) p += "evidence ${e.id} references missing run"
            e.attachmentPath?.let {
                try { ProjectArchive.requireSafeRelative(it) } catch (_: ArchiveException) { p += "evidence ${e.id} has unsafe attachment path" }
            }
        }
        b.claims.filter { it.investigationId !in inv }.forEach { p += "claim ${it.id} references missing investigation" }
        b.capsules.filter { it.investigationId !in inv }.forEach { p += "capsule ${it.id} references missing investigation" }
        return p
    }

    fun allIds(b: ProjectBundle): List<String> =
        listOf(b.project.id) + b.investigations.map { it.id } + b.sessions.map { it.id } + b.builds.map { it.id } +
            b.environments.map { it.id } + b.evidence.map { it.id } + b.claims.map { it.id } + b.plans.map { it.id } +
            b.runs.map { it.id } + b.timeline.map { it.id } + b.capsules.map { it.id } + b.handoffs.map { it.id }
}

/** Claim references that no longer point at live evidence or runs (acceptance E). */
data class DanglingReference(val claimId: String, val missingId: String, val reason: String)

object ReferenceIntegrity {
    fun check(b: ProjectBundle): List<DanglingReference> {
        val live = b.evidence.filter { it.withdrawnAt == null }.map { it.id }.toSet()
        val withdrawn = b.evidence.filter { it.withdrawnAt != null }.map { it.id }.toSet()
        val runs = b.runs.map { it.id }.toSet()
        return b.claims.flatMap { c -> claimProblems(c, live, withdrawn, runs) }
    }

    private fun claimProblems(c: Claim, live: Set<String>, withdrawn: Set<String>, runs: Set<String>) =
        c.evidenceIds.filter { it !in live }.map {
            DanglingReference(c.id, it, if (it in withdrawn) "evidence withdrawn" else "evidence does not exist")
        } + c.testRunIds.filter { it !in runs }.map { DanglingReference(c.id, it, "run does not exist") }
}

/**
 * Re-keys every record of a bundle so it can be imported as a new project without
 * colliding with existing ids. Attachment paths are rebuilt under the new ids.
 */
object IdRemapper {
    fun remap(b: ProjectBundle, newId: () -> String, titleSuffix: String = " (imported)"): Pair<ProjectBundle, Map<String, String>> {
        val ids = BundleValidator.allIds(b).distinct().associateWith { newId() }
        fun m(id: String) = ids[id] ?: id
        fun mo(id: String?) = id?.let(::m)
        val pid = m(b.project.id)
        val pathMap = mutableMapOf<String, String>()
        val out = ProjectBundle(
            project = b.project.copy(id = pid, title = b.project.title + titleSuffix),
            investigations = b.investigations.map { it.copy(id = m(it.id), projectId = pid, knownGoodReferenceId = mo(it.knownGoodReferenceId)) },
            sessions = b.sessions.map { it.copy(id = m(it.id), projectId = pid, investigationId = mo(it.investigationId),
                environmentSnapshotId = mo(it.environmentSnapshotId), buildReferenceId = mo(it.buildReferenceId)) },
            builds = b.builds.map { it.copy(id = m(it.id), projectId = pid) },
            environments = b.environments.map { it.copy(id = m(it.id), projectId = pid) },
            evidence = b.evidence.map { e ->
                val newEid = m(e.id)
                val newPath = e.attachmentPath?.let { old ->
                    val name = old.substringAfterLast('/')
                    "$pid/$newEid/$name".also { pathMap[old] = it }
                }
                e.copy(id = newEid, projectId = pid, investigationId = mo(e.investigationId), sessionId = mo(e.sessionId),
                    testRunId = mo(e.testRunId), derivedFromId = mo(e.derivedFromId), attachmentPath = newPath)
            },
            claims = b.claims.map { it.copy(id = m(it.id), projectId = pid, investigationId = m(it.investigationId),
                evidenceIds = it.evidenceIds.map(::m), testRunIds = it.testRunIds.map(::m)) },
            plans = b.plans.map { it.copy(id = m(it.id), projectId = pid, investigationId = m(it.investigationId)) },
            runs = b.runs.map { it.copy(id = m(it.id), projectId = pid, investigationId = m(it.investigationId),
                testPlanId = m(it.testPlanId), planSnapshot = it.planSnapshot.copy(planId = m(it.planSnapshot.planId)),
                sessionId = mo(it.sessionId), environmentSnapshotId = mo(it.environmentSnapshotId), buildReferenceId = mo(it.buildReferenceId)) },
            timeline = b.timeline.map { it.copy(id = m(it.id), projectId = pid, investigationId = mo(it.investigationId),
                sessionId = mo(it.sessionId), relatedEntityId = mo(it.relatedEntityId)) },
            capsules = b.capsules.map { c -> c.copy(id = m(c.id), projectId = pid, investigationId = m(c.investigationId),
                references = c.references.map { r -> r.copy(id = m(r.id)) }) },
            handoffs = b.handoffs.map { h -> h.copy(id = m(h.id), projectId = pid, investigationId = m(h.investigationId),
                sourceRecordIds = h.sourceRecordIds.map(::m), includedEvidenceIds = h.includedEvidenceIds.map(::m),
                excludedEvidenceIds = h.excludedEvidenceIds.map(::m)) },
        )
        return out to pathMap
    }
}
