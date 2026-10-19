package com.tdater.blackbox.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Pure domain records. Timestamps are epoch milliseconds (UTC). Absent optional
 * values are null; a value that is genuinely not known is modelled explicitly
 * (see [EnvValue.Unknown]) so "unknown" never collapses into "unchanged" or "".
 */

@Serializable
enum class PrivacyPolicy { LOCAL_ONLY, AI_ALLOWED }

@Serializable
enum class ProjectStatus { ACTIVE, PAUSED, DONE, ARCHIVED }

@Serializable
enum class InvestigationStatus { OPEN, TESTING, BLOCKED, RESOLVED, ARCHIVED }

@Serializable
enum class EvidenceKind { TEXT, PHOTO, IMAGE_IMPORT, AUDIO, LOG, FILE, MEASUREMENT }

@Serializable
enum class EvidenceSource { USER, IMPORT, APP_CAPTURE, INSTRUMENTATION }

@Serializable
enum class ProcessingStatus { NONE, PENDING, OK, FAILED }

@Serializable
enum class ClaimClassification { OBSERVED, INFERRED, SUPPORTED_RESULT, UNKNOWN }

@Serializable
enum class ReviewStatus { UNREVIEWED, ACCEPTED, REJECTED }

@Serializable
enum class RunOutcome { NOT_FINISHED, PASS, FAIL, PARTIAL, INCONCLUSIVE, ABANDONED }

@Serializable
enum class GenerationMode { DETERMINISTIC, AI_ASSISTED }

@Serializable
enum class ProjectCategory { SOFTWARE, PHYSICAL, COMPARISON, OTHER, SAMPLE }

@Serializable
data class Project(
    val id: String,
    val title: String,
    val description: String = "",
    val category: ProjectCategory = ProjectCategory.SOFTWARE,
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val createdAt: Long,
    val updatedAt: Long,
    val privacyPolicy: PrivacyPolicy = PrivacyPolicy.LOCAL_ONLY,
    val objective: String = "",
    val labels: List<String> = emptyList(),
    /** True for clearly-labelled synthetic demo data. Never mixed into real records. */
    val isSample: Boolean = false,
)

@Serializable
data class Investigation(
    val id: String,
    val projectId: String,
    val title: String,
    val problemStatement: String = "",
    val desiredOutcome: String = "",
    val status: InvestigationStatus = InvestigationStatus.OPEN,
    val currentQuestion: String = "",
    val nextAction: String = "",
    /** Id of a [TestRun] the user marked as the known-good reference. */
    val knownGoodReferenceId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    /** Incremented whenever this investigation or a record linked to it changes. */
    val revision: Long = 1,
)

@Serializable
data class Session(
    val id: String,
    val projectId: String,
    val investigationId: String? = null,
    val title: String,
    val notes: String = "",
    val startedAt: Long,
    val endedAt: Long? = null,
    val environmentSnapshotId: String? = null,
    val buildReferenceId: String? = null,
)

@Serializable
data class BuildReference(
    val id: String,
    val projectId: String,
    val displayName: String,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val commitRef: String? = null,
    /** Where this reference came from, e.g. "user entry", "imported build.json". */
    val provenance: String = "user entry",
    val createdAt: Long,
)

/** A configuration value. [Unknown] is a first-class value, distinct from absence. */
@Serializable
sealed class EnvValue {
    @Serializable
    @SerialName("known")
    data class Known(val text: String, val unit: String? = null) : EnvValue()

    @Serializable
    @SerialName("unknown")
    data class Unknown(val note: String = "") : EnvValue()
}

@Serializable
data class EnvField(val key: String, val value: EnvValue)

/** Immutable once captured: edits create a new snapshot so old runs keep their context. */
@Serializable
data class EnvironmentSnapshot(
    val id: String,
    val projectId: String,
    val capturedAt: Long,
    val source: String = "user entry",
    val fields: List<EnvField> = emptyList(),
) {
    fun valueOf(key: String): EnvValue? = fields.firstOrNull { it.key == key }?.value
}

@Serializable
data class Evidence(
    val id: String,
    val projectId: String,
    val investigationId: String? = null,
    val sessionId: String? = null,
    val testRunId: String? = null,
    val kind: EvidenceKind,
    val title: String,
    /** Inline text for TEXT/LOG/MEASUREMENT evidence entered in-app. */
    val text: String = "",
    val originalFilename: String? = null,
    val mimeType: String? = null,
    /** Path relative to the app attachment store; never an external content URI. */
    val attachmentPath: String? = null,
    val attachmentSha256: String? = null,
    val attachmentBytes: Long? = null,
    val capturedAt: Long,
    val importedAt: Long? = null,
    val originalTimestamp: Long? = null,
    val source: EvidenceSource = EvidenceSource.USER,
    val userSummary: String = "",
    val labels: List<String> = emptyList(),
    val processingStatus: ProcessingStatus = ProcessingStatus.NONE,
    val processingError: String? = null,
    /** Set when this evidence is a redacted/transformed derivative of another record. */
    val derivedFromId: String? = null,
    /** Set when the record was deleted/replaced; claims referencing it must not stay "supported". */
    val withdrawnAt: Long? = null,
)

@Serializable
data class Claim(
    val id: String,
    val projectId: String,
    val investigationId: String,
    val text: String,
    val classification: ClaimClassification,
    val evidenceIds: List<String> = emptyList(),
    val testRunIds: List<String> = emptyList(),
    val author: String = "user",
    val createdAt: Long,
    val reviewStatus: ReviewStatus = ReviewStatus.UNREVIEWED,
)

@Serializable
data class Measurement(val name: String, val value: Double?, val unit: String? = null)

@Serializable
data class TestPlan(
    val id: String,
    val projectId: String,
    val investigationId: String,
    val title: String,
    val objective: String = "",
    val prerequisites: String = "",
    val steps: List<String> = emptyList(),
    val expectedResult: String,
    val variablesToChange: String = "",
    val variablesToHoldConstant: String = "",
    val measurements: List<String> = emptyList(),
    val isRegression: Boolean = false,
    val revision: Int = 1,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Frozen copy of the plan as it was when a run started. */
@Serializable
data class PlanSnapshot(
    val planId: String,
    val revision: Int,
    val title: String,
    val steps: List<String>,
    val expectedResult: String,
)

@Serializable
data class RunMarker(val at: Long, val note: String)

@Serializable
data class TestRun(
    val id: String,
    val projectId: String,
    val investigationId: String,
    val testPlanId: String,
    val planSnapshot: PlanSnapshot,
    val sessionId: String? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val environmentSnapshotId: String? = null,
    val buildReferenceId: String? = null,
    val intendedChange: String = "",
    val actualChanges: String = "",
    val actualResult: String = "",
    val outcome: RunOutcome = RunOutcome.NOT_FINISHED,
    val deviations: String = "",
    val userNotes: String = "",
    val markers: List<RunMarker> = emptyList(),
    val measurements: List<Measurement> = emptyList(),
) {
    val isFinished: Boolean get() = outcome != RunOutcome.NOT_FINISHED
    val durationMs: Long? get() = endedAt?.let { it - startedAt }
}

@Serializable
enum class TimelineType {
    PROJECT_CREATED, INVESTIGATION_CREATED, EVIDENCE_ADDED, EVIDENCE_WITHDRAWN,
    PLAN_CREATED, PLAN_REVISED, RUN_STARTED, RUN_MARKER, RUN_FINISHED,
    CLAIM_ADDED, BUILD_ADDED, ENVIRONMENT_CAPTURED, CAPSULE_GENERATED, NOTE,
}

@Serializable
data class TimelineEvent(
    val id: String,
    val projectId: String,
    val investigationId: String? = null,
    val sessionId: String? = null,
    val timestamp: Long,
    /** Timestamp reported by an imported source, if any; may differ from [timestamp]. */
    val sourceTimestamp: Long? = null,
    val type: TimelineType,
    val relatedEntityId: String? = null,
    val summary: String,
)

@Serializable
data class SourceRef(val kind: String, val id: String, val label: String)

@Serializable
data class ResumeCapsule(
    val id: String,
    val projectId: String,
    val investigationId: String,
    val sourceRevision: Long,
    val generatedAt: Long,
    val objective: String,
    val knownGoodState: String,
    val currentFailure: String,
    val lastTestedChange: String,
    val nextAction: String,
    val references: List<SourceRef> = emptyList(),
    val generationMode: GenerationMode = GenerationMode.DETERMINISTIC,
    val userEdited: Boolean = false,
)

@Serializable
enum class HandoffReview { NOT_REVIEWED, REVIEWED }

@Serializable
data class HandoffDraft(
    val id: String,
    val projectId: String,
    val investigationId: String,
    val sourceRecordIds: List<String>,
    val includedEvidenceIds: List<String>,
    val excludedEvidenceIds: List<String>,
    val createdAt: Long,
    val format: String = "markdown",
    val content: String,
    val privacyReview: HandoffReview = HandoffReview.NOT_REVIEWED,
)

/** Every record belonging to one project. Unit of archive export/import. */
@Serializable
data class ProjectBundle(
    val project: Project,
    val investigations: List<Investigation> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val builds: List<BuildReference> = emptyList(),
    val environments: List<EnvironmentSnapshot> = emptyList(),
    val evidence: List<Evidence> = emptyList(),
    val claims: List<Claim> = emptyList(),
    val plans: List<TestPlan> = emptyList(),
    val runs: List<TestRun> = emptyList(),
    val timeline: List<TimelineEvent> = emptyList(),
    val capsules: List<ResumeCapsule> = emptyList(),
    val handoffs: List<HandoffDraft> = emptyList(),
) {
    fun forInvestigation(investigationId: String): ProjectBundle = copy(
        investigations = investigations.filter { it.id == investigationId },
        sessions = sessions.filter { it.investigationId == investigationId },
        evidence = evidence.filter { it.investigationId == investigationId },
        claims = claims.filter { it.investigationId == investigationId },
        plans = plans.filter { it.investigationId == investigationId },
        runs = runs.filter { it.investigationId == investigationId },
        timeline = timeline.filter { it.investigationId == investigationId },
        capsules = capsules.filter { it.investigationId == investigationId },
        handoffs = handoffs.filter { it.investigationId == investigationId },
    )

    fun recordCounts(): Map<String, Int> = linkedMapOf(
        "investigations" to investigations.size,
        "sessions" to sessions.size,
        "builds" to builds.size,
        "environments" to environments.size,
        "evidence" to evidence.size,
        "claims" to claims.size,
        "plans" to plans.size,
        "runs" to runs.size,
        "timeline" to timeline.size,
        "capsules" to capsules.size,
        "handoffs" to handoffs.size,
    )
}
