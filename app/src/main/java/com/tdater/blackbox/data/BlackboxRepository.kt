package com.tdater.blackbox.data

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import com.tdater.blackbox.core.archive.IdRemapper
import com.tdater.blackbox.core.archive.ProjectArchive
import com.tdater.blackbox.core.archive.ReadArchive
import com.tdater.blackbox.core.model.BuildReference
import com.tdater.blackbox.core.model.Claim
import com.tdater.blackbox.core.model.ClaimClassification
import com.tdater.blackbox.core.model.EnvField
import com.tdater.blackbox.core.model.EnvironmentSnapshot
import com.tdater.blackbox.core.model.Evidence
import com.tdater.blackbox.core.model.EvidenceKind
import com.tdater.blackbox.core.model.EvidenceSource
import com.tdater.blackbox.core.model.HandoffDraft
import com.tdater.blackbox.core.model.Investigation
import com.tdater.blackbox.core.model.InvestigationStatus
import com.tdater.blackbox.core.model.Measurement
import com.tdater.blackbox.core.model.PlanSnapshot
import com.tdater.blackbox.core.model.PrivacyPolicy
import com.tdater.blackbox.core.model.Project
import com.tdater.blackbox.core.model.ProjectBundle
import com.tdater.blackbox.core.model.ResumeCapsule
import com.tdater.blackbox.core.model.ReviewStatus
import com.tdater.blackbox.core.model.RunMarker
import com.tdater.blackbox.core.model.RunOutcome
import com.tdater.blackbox.core.model.TestPlan
import com.tdater.blackbox.core.model.TestRun
import com.tdater.blackbox.core.model.TimelineEvent
import com.tdater.blackbox.core.model.TimelineType
import com.tdater.blackbox.core.sample.SampleProject
import com.tdater.blackbox.data.db.BlackboxDatabase
import com.tdater.blackbox.data.db.fieldEntities
import com.tdater.blackbox.data.db.toEntity
import com.tdater.blackbox.data.db.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** Single write path for all records. Every mutation is transactional and leaves a timeline entry. */
class BlackboxRepository(
    private val db: BlackboxDatabase,
    val attachments: AttachmentStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.dao()

    fun newId(): String = UUID.randomUUID().toString()

    // ---------- Reads ----------

    fun observeProjects(): Flow<List<Project>> = dao.observeProjects().map { l -> l.map { it.toModel() } }

    fun observeAllInvestigations(): Flow<List<Investigation>> = dao.observeAllInvestigations().map { l -> l.map { it.toModel() } }

    @Suppress("UNCHECKED_CAST")
    fun observeBundle(pid: String): Flow<ProjectBundle?> {
        val flows: List<Flow<List<Any>>> = listOf(
            dao.observeInvestigations(pid), dao.observeSessions(pid), dao.observeBuilds(pid), dao.observeEnvSnapshots(pid),
            dao.observeEnvFields(pid), dao.observeEvidence(pid), dao.observeClaims(pid), dao.observePlans(pid),
            dao.observeRuns(pid), dao.observeTimeline(pid), dao.observeCapsules(pid), dao.observeHandoffs(pid),
        )
        return combine(dao.observeProject(pid), combine(flows) { it.toList() }) { project, a ->
            if (project == null) return@combine null
            val fields = (a[4] as List<com.tdater.blackbox.data.db.EnvFieldEntity>).groupBy { it.snapshotId }
            ProjectBundle(
                project = project.toModel(),
                investigations = (a[0] as List<com.tdater.blackbox.data.db.InvestigationEntity>).map { it.toModel() },
                sessions = (a[1] as List<com.tdater.blackbox.data.db.SessionEntity>).map { it.toModel() },
                builds = (a[2] as List<com.tdater.blackbox.data.db.BuildEntity>).map { it.toModel() },
                environments = (a[3] as List<com.tdater.blackbox.data.db.EnvSnapshotEntity>).map { it.toModel(fields[it.id].orEmpty()) },
                evidence = (a[5] as List<com.tdater.blackbox.data.db.EvidenceEntity>).map { it.toModel() },
                claims = (a[6] as List<com.tdater.blackbox.data.db.ClaimEntity>).map { it.toModel() },
                plans = (a[7] as List<com.tdater.blackbox.data.db.PlanEntity>).map { it.toModel() },
                runs = (a[8] as List<com.tdater.blackbox.data.db.RunEntity>).map { it.toModel() },
                timeline = (a[9] as List<com.tdater.blackbox.data.db.TimelineEntity>).map { it.toModel() },
                capsules = (a[10] as List<com.tdater.blackbox.data.db.CapsuleEntity>).map { it.toModel() },
                handoffs = (a[11] as List<com.tdater.blackbox.data.db.HandoffEntity>).map { it.toModel() },
            )
        }
    }

    suspend fun loadBundle(pid: String): ProjectBundle? = db.withTransaction {
        val p = dao.project(pid) ?: return@withTransaction null
        val fields = dao.envFields(pid).groupBy { it.snapshotId }
        ProjectBundle(
            project = p.toModel(),
            investigations = dao.investigations(pid).map { it.toModel() },
            sessions = dao.sessions(pid).map { it.toModel() },
            builds = dao.builds(pid).map { it.toModel() },
            environments = dao.envSnapshots(pid).map { it.toModel(fields[it.id].orEmpty()) },
            evidence = dao.evidence(pid).map { it.toModel() },
            claims = dao.claims(pid).map { it.toModel() },
            plans = dao.plans(pid).map { it.toModel() },
            runs = dao.runs(pid).map { it.toModel() },
            timeline = dao.timeline(pid).map { it.toModel() },
            capsules = dao.capsules(pid).map { it.toModel() },
            handoffs = dao.handoffs(pid).map { it.toModel() },
        )
    }

    suspend fun allProjects(): List<Project> = dao.allProjects().map { it.toModel() }

    // ---------- Projects ----------

    suspend fun createProject(title: String, objective: String, privacy: PrivacyPolicy = PrivacyPolicy.LOCAL_ONLY): String {
        val now = clock()
        val p = Project(newId(), title.trim(), createdAt = now, updatedAt = now, objective = objective.trim(), privacyPolicy = privacy)
        db.withTransaction {
            dao.upsertProject(p.toEntity())
            event(p.id, null, TimelineType.PROJECT_CREATED, p.id, "Project created: ${p.title}")
        }
        return p.id
    }

    suspend fun updateProject(p: Project) = db.withTransaction {
        dao.upsertProject(p.copy(updatedAt = clock()).toEntity())
    }

    suspend fun deleteProject(pid: String) {
        db.withTransaction { dao.deleteProject(pid) }
        attachments.deleteProject(pid)
    }

    suspend fun loadSample(): String {
        val b = SampleProject.build(::newId, clock())
        insertBundle(b)
        return b.project.id
    }

    // ---------- Investigations ----------

    suspend fun createInvestigation(pid: String, title: String, problem: String, desired: String): String {
        val now = clock()
        val inv = Investigation(newId(), pid, title.trim(), problem.trim(), desired.trim(), createdAt = now, updatedAt = now)
        db.withTransaction {
            dao.upsertInvestigation(inv.toEntity())
            event(pid, inv.id, TimelineType.INVESTIGATION_CREATED, inv.id, "Investigation opened: ${inv.title}")
        }
        return inv.id
    }

    suspend fun updateInvestigation(inv: Investigation) = db.withTransaction {
        val current = dao.investigation(inv.id) ?: return@withTransaction
        dao.upsertInvestigation(inv.copy(revision = current.revision + 1, updatedAt = clock()).toEntity())
        dao.touchProject(inv.projectId, clock())
    }

    suspend fun setKnownGood(investigationId: String, runId: String?) = db.withTransaction {
        val inv = dao.investigation(investigationId) ?: return@withTransaction
        dao.upsertInvestigation(inv.copy(knownGoodReferenceId = runId, revision = inv.revision + 1, updatedAt = clock()))
        event(inv.projectId, inv.id, TimelineType.NOTE, runId,
            if (runId == null) "Known-good reference cleared" else "Run ${runId.take(8)} marked as known-good")
    }

    // ---------- Evidence ----------

    suspend fun addTextEvidence(pid: String, iid: String?, runId: String?, kind: EvidenceKind, title: String, text: String): String {
        val now = clock()
        val e = Evidence(newId(), pid, iid, testRunId = runId, kind = kind,
            title = title.trim().ifEmpty { text.lineSequence().firstOrNull()?.take(60)?.ifBlank { null } ?: "Note" },
            text = text, capturedAt = now, source = EvidenceSource.USER)
        saveEvidence(e, "Added ${kind.name.lowercase()}: ${e.title}")
        return e.id
    }

    /** Copies the content into app storage first, then records it. The source URI is never stored. */
    suspend fun importEvidence(resolver: ContentResolver, uri: Uri, pid: String, iid: String?, runId: String?): String {
        val id = newId()
        val stored = attachments.importFromUri(resolver, uri, pid, id)
        val mime = stored.mimeType
        val kind = when {
            mime?.startsWith("image/") == true -> EvidenceKind.IMAGE_IMPORT
            mime?.startsWith("audio/") == true -> EvidenceKind.AUDIO
            mime == "text/plain" && (stored.displayName.endsWith(".log") || stored.displayName.contains("logcat")) -> EvidenceKind.LOG
            else -> EvidenceKind.FILE
        }
        val now = clock()
        val e = Evidence(id, pid, iid, testRunId = runId, kind = kind, title = stored.displayName,
            originalFilename = stored.displayName, mimeType = mime, attachmentPath = stored.relativePath,
            attachmentSha256 = stored.sha256, attachmentBytes = stored.bytes, capturedAt = now, importedAt = now,
            source = EvidenceSource.IMPORT)
        try {
            saveEvidence(e, "Imported ${stored.displayName}")
        } catch (t: Throwable) {
            attachments.delete(stored.relativePath)
            throw t
        }
        return id
    }

    suspend fun addCapturedFile(file: File, pid: String, iid: String?, runId: String?, kind: EvidenceKind, name: String, mime: String, title: String): String {
        val id = newId()
        val stored = attachments.adoptFile(file, pid, id, name, mime)
        val e = Evidence(id, pid, iid, testRunId = runId, kind = kind, title = title, originalFilename = name, mimeType = mime,
            attachmentPath = stored.relativePath, attachmentSha256 = stored.sha256, attachmentBytes = stored.bytes,
            capturedAt = clock(), source = EvidenceSource.APP_CAPTURE)
        try {
            saveEvidence(e, "Captured ${kind.name.lowercase()}: $title")
        } catch (t: Throwable) {
            attachments.delete(stored.relativePath)
            throw t
        }
        return id
    }

    suspend fun updateEvidenceText(id: String, title: String, summary: String, labels: List<String>) = db.withTransaction {
        val e = dao.evidenceById(id) ?: return@withTransaction
        dao.upsertEvidence(e.copy(title = title, userSummary = summary, labels = labels))
        e.investigationId?.let { dao.bumpRevision(it, clock()) }
    }

    /** Soft delete: record stays (so references can be explained), attachment bytes are removed. */
    suspend fun withdrawEvidence(id: String) {
        val path = db.withTransaction {
            val e = dao.evidenceById(id) ?: return@withTransaction null
            dao.upsertEvidence(e.copy(withdrawnAt = clock()))
            e.investigationId?.let { dao.bumpRevision(it, clock()) }
            event(e.projectId, e.investigationId, TimelineType.EVIDENCE_WITHDRAWN, e.id, "Evidence withdrawn: ${e.title}")
            e.attachmentPath
        }
        path?.let { attachments.delete(it) }
    }

    private suspend fun saveEvidence(e: Evidence, summary: String) = db.withTransaction {
        dao.upsertEvidence(e.toEntity())
        e.investigationId?.let { dao.bumpRevision(it, clock()) }
        dao.touchProject(e.projectId, clock())
        event(e.projectId, e.investigationId, TimelineType.EVIDENCE_ADDED, e.id, summary)
    }

    // ---------- Claims ----------

    suspend fun addClaim(pid: String, iid: String, text: String, cls: ClaimClassification, evidenceIds: List<String>, runIds: List<String>): String {
        val c = Claim(newId(), pid, iid, text.trim(), cls, evidenceIds, runIds, "user", clock())
        db.withTransaction {
            dao.upsertClaim(c.toEntity())
            dao.bumpRevision(iid, clock())
            event(pid, iid, TimelineType.CLAIM_ADDED, c.id, "${cls.name.lowercase().replaceFirstChar { it.uppercase() }}: ${c.text.take(80)}")
        }
        return c.id
    }

    suspend fun setClaimReview(claim: Claim, status: ReviewStatus) = db.withTransaction {
        dao.upsertClaim(claim.copy(reviewStatus = status).toEntity())
        dao.bumpRevision(claim.investigationId, clock())
    }

    // ---------- Builds & environments ----------

    suspend fun addBuild(pid: String, name: String, versionName: String?, versionCode: Long?, commit: String?, provenance: String): String {
        val b = BuildReference(newId(), pid, name.trim(), versionName?.ifBlank { null }, versionCode, commit?.ifBlank { null }, provenance, clock())
        db.withTransaction {
            dao.insertBuild(b.toEntity())
            event(pid, null, TimelineType.BUILD_ADDED, b.id, "Build recorded: ${b.displayName}")
        }
        return b.id
    }

    suspend fun captureEnvironment(pid: String, source: String, fields: List<EnvField>): String {
        val s = EnvironmentSnapshot(newId(), pid, clock(), source, fields.filter { it.key.isNotBlank() })
        db.withTransaction {
            dao.insertEnvSnapshot(s.toEntity())
            dao.insertEnvFields(s.fieldEntities())
            event(pid, null, TimelineType.ENVIRONMENT_CAPTURED, s.id, "Environment snapshot (${s.fields.size} fields)")
        }
        return s.id
    }

    // ---------- Plans & runs ----------

    /** New plan, or a new revision of an existing one. Runs keep their own frozen snapshot. */
    suspend fun savePlan(plan: TestPlan): String = db.withTransaction {
        val now = clock()
        val existing = dao.plan(plan.id)
        if (existing == null) {
            dao.upsertPlan(plan.copy(revision = 1, createdAt = now, updatedAt = now).toEntity())
            event(plan.projectId, plan.investigationId, TimelineType.PLAN_CREATED, plan.id, "Test plan: ${plan.title}")
        } else {
            val rev = existing.revision + 1
            dao.upsertPlan(plan.copy(revision = rev, createdAt = existing.createdAt, updatedAt = now).toEntity())
            event(plan.projectId, plan.investigationId, TimelineType.PLAN_REVISED, plan.id, "Plan revised to r$rev: ${plan.title}")
        }
        dao.bumpRevision(plan.investigationId, now)
        plan.id
    }

    /** The regression flag is not part of a plan's tested content, so toggling it is not a new revision. */
    suspend fun setPlanRegression(planId: String, flag: Boolean) = db.withTransaction {
        val p = dao.plan(planId) ?: return@withTransaction
        dao.upsertPlan(p.copy(isRegression = flag, updatedAt = clock()))
        event(p.projectId, p.investigationId, TimelineType.NOTE, p.id,
            if (flag) "Added to regression checklist: ${p.title}" else "Removed from regression checklist: ${p.title}")
    }

    suspend fun loadAllBundles(): List<ProjectBundle> = dao.allProjects().mapNotNull { loadBundle(it.id) }

    /** Idempotent: if the plan already has an unfinished run, that run is returned instead of a duplicate. */
    suspend fun startRun(planId: String, buildId: String?, envId: String?, intendedChange: String): String = db.withTransaction {
        dao.unfinishedRunForPlan(planId)?.let { return@withTransaction it.id }
        val plan = dao.plan(planId) ?: throw IllegalStateException("Plan not found")
        val now = clock()
        val run = TestRun(newId(), plan.projectId, plan.investigationId, plan.id,
            PlanSnapshot(plan.id, plan.revision, plan.title, plan.steps, plan.expectedResult),
            startedAt = now, environmentSnapshotId = envId, buildReferenceId = buildId, intendedChange = intendedChange.trim())
        dao.insertRun(run.toEntity())
        dao.investigation(plan.investigationId)?.let { inv ->
            val status = if (inv.status == InvestigationStatus.OPEN) InvestigationStatus.TESTING else inv.status
            dao.upsertInvestigation(inv.copy(status = status, revision = inv.revision + 1, updatedAt = now))
        }
        event(plan.projectId, plan.investigationId, TimelineType.RUN_STARTED, run.id, "Run started: ${plan.title} (r${plan.revision})")
        run.id
    }

    suspend fun updateRunDraft(runId: String, actualResult: String, actualChanges: String, deviations: String, notes: String,
                               measurements: List<Measurement>) = db.withTransaction {
        val r = dao.run(runId) ?: return@withTransaction
        if (r.outcome != RunOutcome.NOT_FINISHED) return@withTransaction
        dao.updateRun(r.copy(actualResult = actualResult, actualChanges = actualChanges, deviations = deviations,
            userNotes = notes, measurements = measurements))
    }

    suspend fun addMarker(runId: String, note: String) = db.withTransaction {
        val r = dao.run(runId) ?: return@withTransaction
        if (r.outcome != RunOutcome.NOT_FINISHED) return@withTransaction
        val now = clock()
        dao.updateRun(r.copy(markers = r.markers + RunMarker(now, note.ifBlank { "Marker" })))
        event(r.projectId, r.investigationId, TimelineType.RUN_MARKER, r.id, "Marker: ${note.ifBlank { "Marker" }.take(80)}")
    }

    suspend fun finishRun(runId: String, outcome: RunOutcome, actualResult: String, actualChanges: String, deviations: String,
                          notes: String, measurements: List<Measurement>) = db.withTransaction {
        require(outcome != RunOutcome.NOT_FINISHED)
        val r = dao.run(runId) ?: return@withTransaction
        if (r.outcome != RunOutcome.NOT_FINISHED) return@withTransaction // already finished; never double-finish
        val now = clock()
        dao.updateRun(r.copy(outcome = outcome, endedAt = now, actualResult = actualResult, actualChanges = actualChanges,
            deviations = deviations, userNotes = notes, measurements = measurements))
        dao.bumpRevision(r.investigationId, now)
        dao.touchProject(r.projectId, now)
        val verb = if (outcome == RunOutcome.ABANDONED) "Run abandoned" else "Run finished ${outcome.name}"
        event(r.projectId, r.investigationId, TimelineType.RUN_FINISHED, r.id, "$verb: ${r.planSnapshot.title}")
    }

    // ---------- Capsules & handoffs ----------

    suspend fun saveCapsule(c: ResumeCapsule) = db.withTransaction {
        dao.insertCapsule(c.toEntity())
        event(c.projectId, c.investigationId, TimelineType.CAPSULE_GENERATED, c.id,
            if (c.userEdited) "Resume capsule edited" else "Resume capsule generated")
    }

    suspend fun saveHandoff(h: HandoffDraft) = db.withTransaction { dao.upsertHandoff(h.toEntity()) }

    // ---------- Archives ----------

    suspend fun exportArchive(pid: String, out: OutputStream) =
        ProjectArchive.write(loadBundle(pid) ?: throw IllegalStateException("Project not found"), out, clock()) { attachments.open(it) }

    /** Imports as a NEW project (all ids remapped) so nothing existing can be overwritten. */
    suspend fun importArchive(input: InputStream, stagingDir: File): Pair<String, ReadArchive> {
        val read = ProjectArchive.read(input, stagingDir)
        val (bundle, pathMap) = IdRemapper.remap(read.bundle, ::newId)
        val written = mutableListOf<String>()
        try {
            for ((oldPath, staged) in read.attachments) {
                val newPath = pathMap[oldPath] ?: continue
                val target = attachments.file(newPath)
                target.parentFile?.mkdirs()
                staged.inputStream().use { ins -> target.outputStream().use { ins.copyTo(it) } }
                written += newPath
            }
            insertBundle(bundle.copy(project = bundle.project.copy(updatedAt = clock())))
        } catch (t: Throwable) {
            written.forEach { attachments.delete(it) }
            throw t
        } finally {
            read.attachments.values.forEach { it.delete() }
        }
        return bundle.project.id to read
    }

    private suspend fun insertBundle(b: ProjectBundle) = db.withTransaction {
        dao.upsertProject(b.project.toEntity())
        b.investigations.forEach { dao.upsertInvestigation(it.toEntity()) }
        b.sessions.forEach { dao.upsertSession(it.toEntity()) }
        b.builds.forEach { dao.insertBuild(it.toEntity()) }
        b.environments.forEach { dao.insertEnvSnapshot(it.toEntity()); dao.insertEnvFields(it.fieldEntities()) }
        b.evidence.forEach { dao.upsertEvidence(it.toEntity()) }
        b.claims.forEach { dao.upsertClaim(it.toEntity()) }
        b.plans.forEach { dao.upsertPlan(it.toEntity()) }
        b.runs.forEach { dao.insertRun(it.toEntity()) }
        b.timeline.forEach { dao.insertTimeline(it.toEntity()) }
        b.capsules.forEach { dao.insertCapsule(it.toEntity()) }
        b.handoffs.forEach { dao.upsertHandoff(it.toEntity()) }
    }

    private suspend fun event(pid: String, iid: String?, type: TimelineType, related: String?, summary: String) {
        dao.insertTimeline(TimelineEvent(newId(), pid, iid, null, clock(), null, type, related, summary).toEntity())
    }
}
