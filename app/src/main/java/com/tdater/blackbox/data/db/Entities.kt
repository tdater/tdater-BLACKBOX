package com.tdater.blackbox.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tdater.blackbox.core.model.ClaimClassification
import com.tdater.blackbox.core.model.EvidenceKind
import com.tdater.blackbox.core.model.EvidenceSource
import com.tdater.blackbox.core.model.GenerationMode
import com.tdater.blackbox.core.model.HandoffReview
import com.tdater.blackbox.core.model.InvestigationStatus
import com.tdater.blackbox.core.model.Measurement
import com.tdater.blackbox.core.model.PlanSnapshot
import com.tdater.blackbox.core.model.PrivacyPolicy
import com.tdater.blackbox.core.model.ProcessingStatus
import com.tdater.blackbox.core.model.ProjectCategory
import com.tdater.blackbox.core.model.ProjectStatus
import com.tdater.blackbox.core.model.ReviewStatus
import com.tdater.blackbox.core.model.RunMarker
import com.tdater.blackbox.core.model.RunOutcome
import com.tdater.blackbox.core.model.SourceRef
import com.tdater.blackbox.core.model.TimelineType

private const val P = "projectId"

@Entity(tableName = "project")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val category: ProjectCategory,
    val status: ProjectStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val privacyPolicy: PrivacyPolicy,
    val objective: String,
    val labels: List<String>,
    val isSample: Boolean,
)

@Entity(
    tableName = "investigation",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = [P], onDelete = ForeignKey.CASCADE)],
    indices = [Index(P)],
)
data class InvestigationEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val problemStatement: String,
    val desiredOutcome: String,
    val status: InvestigationStatus,
    val currentQuestion: String,
    val nextAction: String,
    val knownGoodReferenceId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Long,
)

@Entity(
    tableName = "session",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = [P], onDelete = ForeignKey.CASCADE)],
    indices = [Index(P)],
)
data class SessionEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val investigationId: String?,
    val title: String,
    val notes: String,
    val startedAt: Long,
    val endedAt: Long?,
    val environmentSnapshotId: String?,
    val buildReferenceId: String?,
)

@Entity(
    tableName = "build_reference",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = [P], onDelete = ForeignKey.CASCADE)],
    indices = [Index(P)],
)
data class BuildEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val displayName: String,
    val versionName: String?,
    val versionCode: Long?,
    val commitRef: String?,
    val provenance: String,
    val createdAt: Long,
)

@Entity(
    tableName = "environment_snapshot",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = [P], onDelete = ForeignKey.CASCADE)],
    indices = [Index(P)],
)
data class EnvSnapshotEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val capturedAt: Long,
    val source: String,
)

/** One structured key/value row. [known] = false means explicitly unknown (not missing). */
@Entity(
    tableName = "environment_field",
    primaryKeys = ["snapshotId", "position"],
    foreignKeys = [ForeignKey(entity = EnvSnapshotEntity::class, parentColumns = ["id"], childColumns = ["snapshotId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("snapshotId")],
)
data class EnvFieldEntity(
    val snapshotId: String,
    val position: Int,
    val fieldKey: String,
    val known: Boolean,
    val fieldValue: String,
    val unit: String?,
)

@Entity(
    tableName = "evidence",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = [P], onDelete = ForeignKey.CASCADE)],
    indices = [Index(P), Index("investigationId"), Index("testRunId")],
)
data class EvidenceEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val investigationId: String?,
    val sessionId: String?,
    val testRunId: String?,
    val kind: EvidenceKind,
    val title: String,
    val text: String,
    val originalFilename: String?,
    val mimeType: String?,
    val attachmentPath: String?,
    val attachmentSha256: String?,
    val attachmentBytes: Long?,
    val capturedAt: Long,
    val importedAt: Long?,
    val originalTimestamp: Long?,
    val source: EvidenceSource,
    val userSummary: String,
    val labels: List<String>,
    val processingStatus: ProcessingStatus,
    val processingError: String?,
    val derivedFromId: String?,
    val withdrawnAt: Long?,
)

@Entity(
    tableName = "claim",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = [P], onDelete = ForeignKey.CASCADE)],
    indices = [Index(P)],
)
data class ClaimEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val investigationId: String,
    val text: String,
    val classification: ClaimClassification,
    val evidenceIds: List<String>,
    val testRunIds: List<String>,
    val author: String,
    val createdAt: Long,
    val reviewStatus: ReviewStatus,
)

@Entity(
    tableName = "test_plan",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = [P], onDelete = ForeignKey.CASCADE)],
    indices = [Index(P)],
)
data class PlanEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val investigationId: String,
    val title: String,
    val objective: String,
    val prerequisites: String,
    val steps: List<String>,
    val expectedResult: String,
    val variablesToChange: String,
    val variablesToHoldConstant: String,
    val measurements: List<String>,
    val isRegression: Boolean,
    val revision: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "test_run",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = [P], onDelete = ForeignKey.CASCADE)],
    indices = [Index(P), Index("testPlanId")],
)
data class RunEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val investigationId: String,
    val testPlanId: String,
    val planSnapshot: PlanSnapshot,
    val sessionId: String?,
    val startedAt: Long,
    val endedAt: Long?,
    val environmentSnapshotId: String?,
    val buildReferenceId: String?,
    val intendedChange: String,
    val actualChanges: String,
    val actualResult: String,
    val outcome: RunOutcome,
    val deviations: String,
    val userNotes: String,
    val markers: List<RunMarker>,
    val measurements: List<Measurement>,
)

@Entity(
    tableName = "timeline_event",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = [P], onDelete = ForeignKey.CASCADE)],
    indices = [Index(P)],
)
data class TimelineEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val investigationId: String?,
    val sessionId: String?,
    val timestamp: Long,
    val sourceTimestamp: Long?,
    val type: TimelineType,
    val relatedEntityId: String?,
    val summary: String,
)

@Entity(
    tableName = "resume_capsule",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = [P], onDelete = ForeignKey.CASCADE)],
    indices = [Index(P)],
)
data class CapsuleEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val investigationId: String,
    val sourceRevision: Long,
    val generatedAt: Long,
    val objective: String,
    val knownGoodState: String,
    val currentFailure: String,
    val lastTestedChange: String,
    val nextAction: String,
    val sourceRefs: List<SourceRef>,
    val generationMode: GenerationMode,
    val userEdited: Boolean,
)

@Entity(
    tableName = "handoff_draft",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = [P], onDelete = ForeignKey.CASCADE)],
    indices = [Index(P)],
)
data class HandoffEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val investigationId: String,
    val sourceRecordIds: List<String>,
    val includedEvidenceIds: List<String>,
    val excludedEvidenceIds: List<String>,
    val createdAt: Long,
    val format: String,
    val content: String,
    val privacyReview: HandoffReview,
)
