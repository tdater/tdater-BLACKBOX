package com.tdater.blackbox.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import kotlinx.coroutines.flow.Flow

@Dao
interface BlackboxDao {
    // Projects
    @Query("SELECT * FROM project ORDER BY isSample ASC, updatedAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM project WHERE id = :id")
    fun observeProject(id: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM project WHERE id = :id")
    suspend fun project(id: String): ProjectEntity?

    @Query("SELECT * FROM project")
    suspend fun allProjects(): List<ProjectEntity>

    @Upsert suspend fun upsertProject(p: ProjectEntity)

    @Query("UPDATE project SET updatedAt = :now WHERE id = :id")
    suspend fun touchProject(id: String, now: Long)

    @Query("DELETE FROM project WHERE id = :id")
    suspend fun deleteProject(id: String)

    // Investigations
    @Query("SELECT * FROM investigation WHERE projectId = :pid ORDER BY updatedAt DESC")
    fun observeInvestigations(pid: String): Flow<List<InvestigationEntity>>

    @Query("SELECT * FROM investigation WHERE projectId = :pid")
    suspend fun investigations(pid: String): List<InvestigationEntity>

    @Query("SELECT * FROM investigation WHERE id = :id")
    suspend fun investigation(id: String): InvestigationEntity?

    @Query("SELECT * FROM investigation ORDER BY updatedAt DESC")
    fun observeAllInvestigations(): Flow<List<InvestigationEntity>>

    @Upsert suspend fun upsertInvestigation(i: InvestigationEntity)

    @Query("UPDATE investigation SET revision = revision + 1, updatedAt = :now WHERE id = :id")
    suspend fun bumpRevision(id: String, now: Long)

    // Sessions
    @Query("SELECT * FROM session WHERE projectId = :pid ORDER BY startedAt DESC")
    fun observeSessions(pid: String): Flow<List<SessionEntity>>
    @Query("SELECT * FROM session WHERE projectId = :pid") suspend fun sessions(pid: String): List<SessionEntity>
    @Upsert suspend fun upsertSession(s: SessionEntity)

    // Builds
    @Query("SELECT * FROM build_reference WHERE projectId = :pid ORDER BY createdAt DESC")
    fun observeBuilds(pid: String): Flow<List<BuildEntity>>
    @Query("SELECT * FROM build_reference WHERE projectId = :pid") suspend fun builds(pid: String): List<BuildEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertBuild(b: BuildEntity)

    // Environment snapshots (immutable: insert only)
    @Query("SELECT * FROM environment_snapshot WHERE projectId = :pid ORDER BY capturedAt DESC")
    fun observeEnvSnapshots(pid: String): Flow<List<EnvSnapshotEntity>>
    @Query("SELECT * FROM environment_snapshot WHERE projectId = :pid") suspend fun envSnapshots(pid: String): List<EnvSnapshotEntity>
    @Query("SELECT f.* FROM environment_field f JOIN environment_snapshot s ON s.id = f.snapshotId WHERE s.projectId = :pid")
    fun observeEnvFields(pid: String): Flow<List<EnvFieldEntity>>
    @Query("SELECT f.* FROM environment_field f JOIN environment_snapshot s ON s.id = f.snapshotId WHERE s.projectId = :pid")
    suspend fun envFields(pid: String): List<EnvFieldEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertEnvSnapshot(s: EnvSnapshotEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertEnvFields(f: List<EnvFieldEntity>)

    // Evidence
    @Query("SELECT * FROM evidence WHERE projectId = :pid ORDER BY capturedAt DESC")
    fun observeEvidence(pid: String): Flow<List<EvidenceEntity>>
    @Query("SELECT * FROM evidence WHERE projectId = :pid") suspend fun evidence(pid: String): List<EvidenceEntity>
    @Query("SELECT * FROM evidence WHERE id = :id") suspend fun evidenceById(id: String): EvidenceEntity?
    @Upsert suspend fun upsertEvidence(e: EvidenceEntity)

    // Claims
    @Query("SELECT * FROM claim WHERE projectId = :pid ORDER BY createdAt DESC")
    fun observeClaims(pid: String): Flow<List<ClaimEntity>>
    @Query("SELECT * FROM claim WHERE projectId = :pid") suspend fun claims(pid: String): List<ClaimEntity>
    @Upsert suspend fun upsertClaim(c: ClaimEntity)

    // Plans
    @Query("SELECT * FROM test_plan WHERE projectId = :pid ORDER BY createdAt ASC")
    fun observePlans(pid: String): Flow<List<PlanEntity>>
    @Query("SELECT * FROM test_plan WHERE projectId = :pid") suspend fun plans(pid: String): List<PlanEntity>
    @Query("SELECT * FROM test_plan WHERE id = :id") suspend fun plan(id: String): PlanEntity?
    @Upsert suspend fun upsertPlan(p: PlanEntity)

    // Runs
    @Query("SELECT * FROM test_run WHERE projectId = :pid ORDER BY startedAt DESC")
    fun observeRuns(pid: String): Flow<List<RunEntity>>
    @Query("SELECT * FROM test_run WHERE projectId = :pid") suspend fun runs(pid: String): List<RunEntity>
    @Query("SELECT * FROM test_run WHERE id = :id") suspend fun run(id: String): RunEntity?
    @Query("SELECT * FROM test_run WHERE testPlanId = :planId AND outcome = 'NOT_FINISHED' LIMIT 1")
    suspend fun unfinishedRunForPlan(planId: String): RunEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRun(r: RunEntity)
    @Update suspend fun updateRun(r: RunEntity)

    // Timeline
    @Query("SELECT * FROM timeline_event WHERE projectId = :pid ORDER BY timestamp DESC")
    fun observeTimeline(pid: String): Flow<List<TimelineEntity>>
    @Query("SELECT * FROM timeline_event WHERE projectId = :pid") suspend fun timeline(pid: String): List<TimelineEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTimeline(t: TimelineEntity)

    // Capsules (insert-only; an edited capsule is saved as a new record)
    @Query("SELECT * FROM resume_capsule WHERE projectId = :pid ORDER BY generatedAt DESC")
    fun observeCapsules(pid: String): Flow<List<CapsuleEntity>>
    @Query("SELECT * FROM resume_capsule WHERE projectId = :pid") suspend fun capsules(pid: String): List<CapsuleEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCapsule(c: CapsuleEntity)

    // Handoffs
    @Query("SELECT * FROM handoff_draft WHERE projectId = :pid ORDER BY createdAt DESC")
    fun observeHandoffs(pid: String): Flow<List<HandoffEntity>>
    @Query("SELECT * FROM handoff_draft WHERE projectId = :pid") suspend fun handoffs(pid: String): List<HandoffEntity>
    @Upsert suspend fun upsertHandoff(h: HandoffEntity)

    // Follow-up reminders
    @Query("SELECT * FROM investigation WHERE followUpAt IS NOT NULL ORDER BY followUpAt ASC")
    fun observeFollowUps(): Flow<List<InvestigationEntity>>
    @Query("SELECT * FROM investigation WHERE followUpAt IS NOT NULL")
    suspend fun followUps(): List<InvestigationEntity>

    // Saved procedures (checklists)
    @Query("SELECT * FROM saved_procedure ORDER BY updatedAt DESC")
    fun observeProcedures(): Flow<List<ProcedureEntity>>
    @Query("SELECT * FROM saved_procedure") suspend fun procedures(): List<ProcedureEntity>
    @Query("SELECT * FROM saved_procedure WHERE id = :id") suspend fun procedure(id: String): ProcedureEntity?
    @Upsert suspend fun upsertProcedure(p: ProcedureEntity)
    @Query("DELETE FROM saved_procedure WHERE id = :id") suspend fun deleteProcedure(id: String)
}

@Database(
    entities = [
        ProjectEntity::class, InvestigationEntity::class, SessionEntity::class, BuildEntity::class,
        EnvSnapshotEntity::class, EnvFieldEntity::class, EvidenceEntity::class, ClaimEntity::class,
        PlanEntity::class, RunEntity::class, TimelineEntity::class, CapsuleEntity::class, HandoffEntity::class,
        ProcedureEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BlackboxDatabase : RoomDatabase() {
    abstract fun dao(): BlackboxDao

    companion object {
        const val NAME = "blackbox.db"

        /** v2: project templates and condition fields, investigation follow-ups, saved procedures. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE project ADD COLUMN templateId TEXT NOT NULL DEFAULT 'custom'")
                db.execSQL("ALTER TABLE project ADD COLUMN fieldKeys TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE investigation ADD COLUMN followUpAt INTEGER")
                db.execSQL("ALTER TABLE investigation ADD COLUMN followUpNote TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_procedure` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                        "`steps` TEXT NOT NULL, `expectedResult` TEXT NOT NULL, `templateId` TEXT NOT NULL, `sourceProjectId` TEXT, " +
                        "`sourcePlanId` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2)

        /** Every schema bump gets an explicit Migration in [MIGRATIONS]; destructive migration is not allowed. */
        fun build(context: Context, factory: SupportSQLiteOpenHelper.Factory? = null): BlackboxDatabase =
            Room.databaseBuilder(context, BlackboxDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .apply { if (factory != null) openHelperFactory(factory) }
                .build()
    }
}
