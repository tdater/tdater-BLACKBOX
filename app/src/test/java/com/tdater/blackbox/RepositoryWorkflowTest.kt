package com.tdater.blackbox

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tdater.blackbox.core.handoff.HandoffGenerator
import com.tdater.blackbox.core.handoff.HandoffOptions
import com.tdater.blackbox.core.model.EvidenceKind
import com.tdater.blackbox.core.model.Measurement
import com.tdater.blackbox.core.model.RunOutcome
import com.tdater.blackbox.core.model.TestPlan
import com.tdater.blackbox.core.resume.Freshness
import com.tdater.blackbox.core.resume.ResumeGenerator
import com.tdater.blackbox.data.AttachmentStore
import com.tdater.blackbox.data.BlackboxRepository
import com.tdater.blackbox.data.db.BlackboxDatabase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File

/** Repository-level acceptance checks (A, B, C-duplicates, F, H) against a real Room database file. */
@RunWith(RobolectricTestRunner::class)
class RepositoryWorkflowTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val attachRoot = File(ctx.filesDir, "attachments-test")

    private fun open(name: String): Pair<BlackboxDatabase, BlackboxRepository> {
        val db = Room.databaseBuilder(ctx, BlackboxDatabase::class.java, name).allowMainThreadQueries().build()
        return db to BlackboxRepository(db, AttachmentStore(attachRoot))
    }

    @Test
    fun offlineInvestigationSurvivesRelaunch() = runTest {
        val (db1, repo) = open("a.db")
        val pid = repo.createProject("KITT", "OBD stays live")
        val iid = repo.createInvestigation(pid, "OBD freeze after screen-off", "Values freeze", "Values resume")
        repo.addTextEvidence(pid, iid, null, EvidenceKind.TEXT, "", "RPM stuck at 812")
        val planId = repo.savePlan(TestPlan(repo.newId(), pid, iid, "Screen off 60s", steps = listOf("off", "wait", "on"),
            expectedResult = "RPM resumes", measurements = listOf("Recovery"), createdAt = 0, updatedAt = 0))
        val runId = repo.startRun(planId, null, null, "baseline")
        // Starting again while unfinished returns the same run instead of a duplicate.
        assertEquals(runId, repo.startRun(planId, null, null, "baseline"))
        repo.finishRun(runId, RunOutcome.FAIL, "Frozen", "", "", "", listOf(Measurement("Recovery", null, "s")))
        // A second finish is ignored (no double-finish).
        repo.finishRun(runId, RunOutcome.PASS, "x", "", "", "", emptyList())

        val b = repo.loadBundle(pid)!!
        val capsule = ResumeGenerator.generate(b, iid, repo.newId(), 1)
        repo.saveCapsule(capsule)
        val md = HandoffGenerator.generate(b, iid, HandoffOptions(includedEvidenceIds = b.evidence.map { it.id }.toSet())).markdown
        assertTrue(md.contains("Frozen"))
        db1.close()

        // "Relaunch": new database instance on the same file.
        val (db2, repo2) = open("a.db")
        val again = repo2.loadBundle(pid)!!
        assertEquals(1, again.investigations.size)
        assertEquals(1, again.runs.size)
        assertEquals(RunOutcome.FAIL, again.runs[0].outcome)
        assertEquals(1, again.evidence.size)
        assertEquals(1, again.capsules.size)
        assertTrue(again.timeline.isNotEmpty())

        // F: adding a result makes the saved capsule stale without rewriting it.
        assertEquals(Freshness.CURRENT, ResumeGenerator.freshness(again.capsules[0], again.investigations[0]))
        repo2.addTextEvidence(pid, iid, runId, EvidenceKind.LOG, "log", "E/Obd: broken pipe")
        val after = repo2.loadBundle(pid)!!
        assertEquals(Freshness.STALE, ResumeGenerator.freshness(after.capsules[0], after.investigations[0]))
        assertEquals(capsule.currentFailure, after.capsules[0].currentFailure)
        db2.close()
    }

    @Test
    fun importedAttachmentSurvivesLossOfOriginal() = runTest {
        val (db, repo) = open("b.db")
        val pid = repo.createProject("P", "")
        val original = File(ctx.cacheDir, "screenshot.png").apply { writeBytes(ByteArray(4096) { it.toByte() }) }
        val eid = repo.importEvidence(ctx.contentResolver, Uri.fromFile(original), pid, null, null)
        original.delete()
        val e = repo.loadBundle(pid)!!.evidence.single { it.id == eid }
        assertNotNull(e.attachmentPath)
        val stored = repo.attachments.file(e.attachmentPath!!)
        assertTrue(stored.isFile)
        assertEquals(4096L, stored.length())
        assertEquals(e.attachmentBytes, stored.length())
        db.close()
    }

    @Test
    fun archiveRoundTripThroughRepository() = runTest {
        val (db, repo) = open("c.db")
        val pid = repo.loadSample()
        val src = File(ctx.cacheDir, "note.txt").apply { writeText("attached") }
        val iid = repo.loadBundle(pid)!!.investigations[0].id
        repo.importEvidence(ctx.contentResolver, Uri.fromFile(src), pid, iid, null)
        val before = repo.loadBundle(pid)!!

        val out = ByteArrayOutputStream()
        val manifest = repo.exportArchive(pid, out).manifest
        assertEquals(1, manifest.files.size)
        val (newPid, _) = repo.importArchive(out.toByteArray().inputStream(), File(ctx.cacheDir, "stage"))
        val after = repo.loadBundle(newPid)!!
        assertTrue(newPid != pid)
        assertEquals(before.recordCounts(), after.recordCounts())
        val att = after.evidence.single { it.attachmentPath != null }
        assertEquals(before.evidence.single { it.attachmentPath != null }.attachmentSha256, att.attachmentSha256)
        assertEquals("attached", repo.attachments.file(att.attachmentPath!!).readText())
        db.close()
    }
}
