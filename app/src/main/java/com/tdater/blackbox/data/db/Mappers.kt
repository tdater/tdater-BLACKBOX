package com.tdater.blackbox.data.db

import com.tdater.blackbox.core.model.BuildReference
import com.tdater.blackbox.core.model.Claim
import com.tdater.blackbox.core.model.EnvField
import com.tdater.blackbox.core.model.EnvValue
import com.tdater.blackbox.core.model.EnvironmentSnapshot
import com.tdater.blackbox.core.model.Evidence
import com.tdater.blackbox.core.model.HandoffDraft
import com.tdater.blackbox.core.model.Investigation
import com.tdater.blackbox.core.model.Project
import com.tdater.blackbox.core.model.ResumeCapsule
import com.tdater.blackbox.core.model.Session
import com.tdater.blackbox.core.model.TestPlan
import com.tdater.blackbox.core.model.TestRun
import com.tdater.blackbox.core.model.TimelineEvent

fun ProjectEntity.toModel() = Project(id, title, description, category, status, createdAt, updatedAt, privacyPolicy, objective, labels, isSample)
fun Project.toEntity() = ProjectEntity(id, title, description, category, status, createdAt, updatedAt, privacyPolicy, objective, labels, isSample)

fun InvestigationEntity.toModel() = Investigation(id, projectId, title, problemStatement, desiredOutcome, status, currentQuestion, nextAction, knownGoodReferenceId, createdAt, updatedAt, revision)
fun Investigation.toEntity() = InvestigationEntity(id, projectId, title, problemStatement, desiredOutcome, status, currentQuestion, nextAction, knownGoodReferenceId, createdAt, updatedAt, revision)

fun SessionEntity.toModel() = Session(id, projectId, investigationId, title, notes, startedAt, endedAt, environmentSnapshotId, buildReferenceId)
fun Session.toEntity() = SessionEntity(id, projectId, investigationId, title, notes, startedAt, endedAt, environmentSnapshotId, buildReferenceId)

fun BuildEntity.toModel() = BuildReference(id, projectId, displayName, versionName, versionCode, commitRef, provenance, createdAt)
fun BuildReference.toEntity() = BuildEntity(id, projectId, displayName, versionName, versionCode, commitRef, provenance, createdAt)

fun EnvSnapshotEntity.toModel(fields: List<EnvFieldEntity>) = EnvironmentSnapshot(
    id, projectId, capturedAt, source,
    fields.sortedBy { it.position }.map {
        EnvField(it.fieldKey, if (it.known) EnvValue.Known(it.fieldValue, it.unit) else EnvValue.Unknown(it.fieldValue))
    },
)
fun EnvironmentSnapshot.toEntity() = EnvSnapshotEntity(id, projectId, capturedAt, source)
fun EnvironmentSnapshot.fieldEntities() = fields.mapIndexed { i, f ->
    when (val v = f.value) {
        is EnvValue.Known -> EnvFieldEntity(id, i, f.key, true, v.text, v.unit)
        is EnvValue.Unknown -> EnvFieldEntity(id, i, f.key, false, v.note, null)
    }
}

fun EvidenceEntity.toModel() = Evidence(id, projectId, investigationId, sessionId, testRunId, kind, title, text, originalFilename, mimeType,
    attachmentPath, attachmentSha256, attachmentBytes, capturedAt, importedAt, originalTimestamp, source, userSummary, labels,
    processingStatus, processingError, derivedFromId, withdrawnAt)
fun Evidence.toEntity() = EvidenceEntity(id, projectId, investigationId, sessionId, testRunId, kind, title, text, originalFilename, mimeType,
    attachmentPath, attachmentSha256, attachmentBytes, capturedAt, importedAt, originalTimestamp, source, userSummary, labels,
    processingStatus, processingError, derivedFromId, withdrawnAt)

fun ClaimEntity.toModel() = Claim(id, projectId, investigationId, text, classification, evidenceIds, testRunIds, author, createdAt, reviewStatus)
fun Claim.toEntity() = ClaimEntity(id, projectId, investigationId, text, classification, evidenceIds, testRunIds, author, createdAt, reviewStatus)

fun PlanEntity.toModel() = TestPlan(id, projectId, investigationId, title, objective, prerequisites, steps, expectedResult,
    variablesToChange, variablesToHoldConstant, measurements, isRegression, revision, createdAt, updatedAt)
fun TestPlan.toEntity() = PlanEntity(id, projectId, investigationId, title, objective, prerequisites, steps, expectedResult,
    variablesToChange, variablesToHoldConstant, measurements, isRegression, revision, createdAt, updatedAt)

fun RunEntity.toModel() = TestRun(id, projectId, investigationId, testPlanId, planSnapshot, sessionId, startedAt, endedAt,
    environmentSnapshotId, buildReferenceId, intendedChange, actualChanges, actualResult, outcome, deviations, userNotes, markers, measurements)
fun TestRun.toEntity() = RunEntity(id, projectId, investigationId, testPlanId, planSnapshot, sessionId, startedAt, endedAt,
    environmentSnapshotId, buildReferenceId, intendedChange, actualChanges, actualResult, outcome, deviations, userNotes, markers, measurements)

fun TimelineEntity.toModel() = TimelineEvent(id, projectId, investigationId, sessionId, timestamp, sourceTimestamp, type, relatedEntityId, summary)
fun TimelineEvent.toEntity() = TimelineEntity(id, projectId, investigationId, sessionId, timestamp, sourceTimestamp, type, relatedEntityId, summary)

fun CapsuleEntity.toModel() = ResumeCapsule(id, projectId, investigationId, sourceRevision, generatedAt, objective, knownGoodState,
    currentFailure, lastTestedChange, nextAction, sourceRefs, generationMode, userEdited)
fun ResumeCapsule.toEntity() = CapsuleEntity(id, projectId, investigationId, sourceRevision, generatedAt, objective, knownGoodState,
    currentFailure, lastTestedChange, nextAction, references, generationMode, userEdited)

fun HandoffEntity.toModel() = HandoffDraft(id, projectId, investigationId, sourceRecordIds, includedEvidenceIds, excludedEvidenceIds,
    createdAt, format, content, privacyReview)
fun HandoffDraft.toEntity() = HandoffEntity(id, projectId, investigationId, sourceRecordIds, includedEvidenceIds, excludedEvidenceIds,
    createdAt, format, content, privacyReview)
