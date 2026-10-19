package com.tdater.blackbox.core

import com.tdater.blackbox.core.ai.AiGateway
import com.tdater.blackbox.core.ai.AiProvider
import com.tdater.blackbox.core.ai.AiResult
import com.tdater.blackbox.core.ai.AiSuggestion
import com.tdater.blackbox.core.ai.ContextItem
import com.tdater.blackbox.core.ai.ContextPacket
import com.tdater.blackbox.core.archive.ArchiveException
import com.tdater.blackbox.core.archive.BundleValidator
import com.tdater.blackbox.core.archive.IdRemapper
import com.tdater.blackbox.core.archive.ProjectArchive
import com.tdater.blackbox.core.archive.ReferenceIntegrity
import com.tdater.blackbox.core.compare.ComparisonEngine
import com.tdater.blackbox.core.compare.DiffStatus
import com.tdater.blackbox.core.handoff.HandoffGenerator
import com.tdater.blackbox.core.handoff.HandoffOptions
import com.tdater.blackbox.core.handoff.SensitiveScanner
import com.tdater.blackbox.core.model.EvidenceKind
import com.tdater.blackbox.core.model.Measurement
import com.tdater.blackbox.core.model.PrivacyPolicy
import com.tdater.blackbox.core.model.ProjectBundle
import com.tdater.blackbox.core.model.RunOutcome
import com.tdater.blackbox.core.resume.Freshness
import com.tdater.blackbox.core.resume.ResumeGenerator
import com.tdater.blackbox.core.sample.SampleProject
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CoreAcceptanceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val now = 1_760_000_000_000L
    private fun id() = UUID.randomUUID().toString()
    private fun sample(): ProjectBundle = SampleProject.build(::id, now)

    // D. Honest comparison
    @Test
    fun missingOrUnknownEnvironmentValueIsNeverUnchanged() {
        val b = sample()
        val (r1, r2) = b.runs
        val report = ComparisonEngine.compareRuns(b, r1.id, r2.id)
        val conn = report.rows.first { it.key == "Connection" }
        assertEquals(DiffStatus.UNKNOWN, conn.status)
        val device = report.rows.first { it.key == "Device" }
        assertEquals(DiffStatus.UNCHANGED_KNOWN, device.status)
        assertTrue(report.warnings.any { "unknown or missing" in it })

        // Remove a key from one side entirely -> MISSING, not unchanged.
        val env2 = b.environments[1]
        val b2 = b.copy(environments = listOf(b.environments[0], env2.copy(fields = env2.fields.filter { it.key != "Adapter" })))
        val adapter = ComparisonEngine.compareRuns(b2, r1.id, r2.id).rows.first { it.key == "Adapter" }
        assertEquals(DiffStatus.MISSING, adapter.status)
    }

    @Test
    fun incompatibleUnitsAreNotCompared() {
        val b = sample()
        val runs = b.runs.toMutableList()
        runs[0] = runs[0].copy(measurements = listOf(Measurement("Recovery time", 2000.0, "ms")))
        val report = ComparisonEngine.compareRuns(b.copy(runs = runs), runs[0].id, runs[1].id)
        val row = report.rows.first { it.key == "Recovery time" }
        assertEquals(DiffStatus.NOT_COMPARABLE, row.status)
        assertTrue("Units differ" in row.explanation)
    }

    @Test
    fun buildChangeIsReportedFromRecordsOnly() {
        val b = sample()
        val report = ComparisonEngine.compareRuns(b, b.runs[0].id, b.runs[1].id)
        val v = report.rows.first { it.key == "versionName" }
        assertEquals(DiffStatus.CHANGED, v.status)
        assertEquals(b.builds[0].id, v.leftSource)
        // No build on either side -> MISSING, never invented.
        val noBuild = b.copy(runs = b.runs.map { it.copy(buildReferenceId = null) })
        val row = ComparisonEngine.compareRuns(noBuild, b.runs[0].id, b.runs[1].id).rows.first { it.key == "Build" }
        assertEquals(DiffStatus.MISSING, row.status)
    }

    // E. Evidence integrity
    @Test
    fun withdrawnEvidenceIsFlaggedAsDanglingClaimReference() {
        val b = sample()
        assertTrue(ReferenceIntegrity.check(b).isEmpty())
        val logId = b.evidence.first { it.kind == EvidenceKind.LOG }.id
        val withdrawn = b.copy(evidence = b.evidence.map { if (it.id == logId) it.copy(withdrawnAt = now) else it })
        val dangling = ReferenceIntegrity.check(withdrawn)
        assertEquals(2, dangling.size)
        assertTrue(dangling.all { it.missingId == logId && it.reason == "evidence withdrawn" })
    }

    // F. Resume freshness
    @Test
    fun capsuleBecomesStaleWithoutBeingRewritten() {
        val b = sample()
        val inv = b.investigations[0]
        val capsule = ResumeGenerator.generate(b, inv.id, id(), now)
        assertEquals(Freshness.CURRENT, ResumeGenerator.freshness(capsule, inv))
        assertTrue(capsule.knownGoodState.startsWith("Known-good"))
        val bumped = inv.copy(revision = inv.revision + 1)
        assertEquals(Freshness.STALE, ResumeGenerator.freshness(capsule, bumped))
        // The old capsule object is untouched; regeneration yields a new one.
        val fresh = ResumeGenerator.generate(b.copy(investigations = listOf(bumped)), inv.id, id(), now + 1)
        assertEquals(inv.revision, capsule.sourceRevision)
        assertEquals(bumped.revision, fresh.sourceRevision)
    }

    @Test
    fun capsuleSaysSoWhenNothingRecorded() {
        val b = sample()
        val inv = b.investigations[0].copy(knownGoodReferenceId = null, nextAction = "")
        val empty = b.copy(investigations = listOf(inv), runs = emptyList(), evidence = emptyList(), claims = emptyList())
        val c = ResumeGenerator.generate(empty, inv.id, id(), now)
        assertEquals(ResumeGenerator.NO_KNOWN_GOOD, c.knownGoodState)
        assertEquals(ResumeGenerator.NO_NEXT_ACTION, c.nextAction)
        assertEquals(ResumeGenerator.NO_TESTED_CHANGE, c.lastTestedChange)
    }

    // G. Handoff review
    @Test
    fun excludedEvidenceNeverAppearsInHandoff() {
        val b = sample()
        val inv = b.investigations[0]
        val log = b.evidence.first { it.kind == EvidenceKind.LOG }
        val note = b.evidence.first { it.kind == EvidenceKind.TEXT }
        val r = HandoffGenerator.generate(b, inv.id, HandoffOptions(includedEvidenceIds = setOf(note.id)))
        assertFalse(log.title in r.markdown)
        assertFalse("Broken pipe" in r.markdown)
        assertTrue(note.title in r.markdown)
        assertEquals(listOf(log.id), r.excludedEvidenceIds)
        assertTrue(HandoffGenerator.ATTACHMENT_NOTICE in r.markdown)
        for (n in 1..10) assertTrue("## $n." in r.markdown, "missing section $n")
        // Claim citing the excluded log shows it as excluded, not by id.
        assertFalse(log.id.take(8) in r.markdown)
    }

    @Test
    fun longExcerptTruncationIsDisclosed() {
        val b = sample()
        val big = b.evidence[0].copy(text = "x".repeat(5000))
        val bb = b.copy(evidence = listOf(big) + b.evidence.drop(1))
        val r = HandoffGenerator.generate(bb, b.investigations[0].id, HandoffOptions(includedEvidenceIds = setOf(big.id)))
        assertTrue("Excerpt truncated" in r.markdown)
    }

    @Test
    fun sensitiveScannerFindsCommonPatterns() {
        val hits = SensitiveScanner.scan("contact me at bob@example.com from 10.0.0.12 password: hunter2")
        assertTrue(hits.any { "email" in it })
        assertTrue(hits.any { "IPv4" in it })
        assertTrue(hits.any { "password" in it })
    }

    // H. Archive round-trip and traversal rejection
    @Test
    fun archiveRoundTripPreservesRecordsRelationsAndChecksums() {
        val base = sample()
        val pid = base.project.id
        val ev = base.evidence[0]
        val path = "$pid/${ev.id}/capture.txt"
        val bytes = "attachment-bytes-é".toByteArray()
        val b = base.copy(evidence = listOf(ev.copy(attachmentPath = path, attachmentSha256 = ProjectArchive.sha256(bytes),
            attachmentBytes = bytes.size.toLong())) + base.evidence.drop(1))
        val out = ByteArrayOutputStream()
        val res = ProjectArchive.write(b, out, now) { p -> if (p == path) ByteArrayInputStream(bytes) else null }
        assertTrue(res.manifest.missingAttachments.isEmpty())

        val read = ProjectArchive.read(ByteArrayInputStream(out.toByteArray()), tmp.newFolder("stage"))
        assertEquals(b, read.bundle)
        assertEquals(b.recordCounts(), read.manifest.recordCounts)
        assertTrue(read.attachments.getValue(path).readBytes().contentEquals(bytes))

        val (imported, paths) = IdRemapper.remap(read.bundle, ::id)
        assertTrue(BundleValidator.validate(imported).isEmpty())
        assertEquals(b.recordCounts(), imported.recordCounts())
        assertTrue(BundleValidator.allIds(imported).intersect(BundleValidator.allIds(b).toSet()).isEmpty())
        val newEv = imported.evidence[0]
        assertEquals(paths[path], newEv.attachmentPath)
        assertEquals(ev.attachmentSha256 ?: ProjectArchive.sha256(bytes), newEv.attachmentSha256)
        // Relationships survive the remap.
        val run = imported.runs.first { it.outcome == RunOutcome.FAIL }
        assertTrue(imported.evidence.any { it.testRunId == run.id })
        assertEquals(imported.runs.first { it.outcome == RunOutcome.PASS }.id, imported.investigations[0].knownGoodReferenceId)
    }

    @Test
    fun traversalArchiveIsRejected() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("attachments/../../evil.sh")); z.write("boom".toByteArray()); z.closeEntry()
        }
        val stage = tmp.newFolder("stage2")
        val e = assertFailsWith<ArchiveException> { ProjectArchive.read(ByteArrayInputStream(out.toByteArray()), stage) }
        assertTrue("Unsafe" in e.message!!)
        assertFalse(java.io.File(stage.parentFile, "evil.sh").exists())
        assertTrue(stage.listFiles()!!.isEmpty())
    }

    @Test
    fun tamperedOrMalformedArchiveIsRejected() {
        val b = sample()
        val out = ByteArrayOutputStream()
        ProjectArchive.write(b, out, now) { null }
        // Re-pack with a modified bundle.json but the original manifest.
        val entries = mutableMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { z ->
            while (true) { val e = z.nextEntry ?: break; entries[e.name] = z.readBytes() }
        }
        entries["bundle.json"] = entries.getValue("bundle.json").decodeToString().replace("SAMPLE", "SAMPL3").toByteArray()
        val tampered = ByteArrayOutputStream()
        ZipOutputStream(tampered).use { z -> entries.forEach { (k, v) -> z.putNextEntry(ZipEntry(k)); z.write(v); z.closeEntry() } }
        assertFailsWith<ArchiveException> { ProjectArchive.read(ByteArrayInputStream(tampered.toByteArray()), tmp.newFolder("s3")) }
        assertFailsWith<ArchiveException> { ProjectArchive.read(ByteArrayInputStream("not a zip".toByteArray()), tmp.newFolder("s4")) }
    }

    @Test
    fun missingAttachmentIsDisclosedInManifest() {
        val b = sample()
        val ev = b.evidence[0].copy(attachmentPath = "${b.project.id}/${b.evidence[0].id}/gone.jpg")
        val res = ProjectArchive.write(b.copy(evidence = listOf(ev)), ByteArrayOutputStream(), now) { null }
        assertEquals(listOf(ev.attachmentPath!!), res.manifest.missingAttachments)
    }

    // J. Privacy
    private class SpyProvider(val reply: suspend (ContextPacket) -> List<AiSuggestion> = { emptyList() }) : AiProvider {
        var calls = 0
        override val name = "spy"
        override suspend fun complete(packet: ContextPacket): List<AiSuggestion> { calls++; return reply(packet) }
    }

    @Test
    fun localOnlyProjectNeverReachesProvider() = runTest {
        val local = sample().project.copy(privacyPolicy = PrivacyPolicy.LOCAL_ONLY)
        val allowed = sample().project.copy(privacyPolicy = PrivacyPolicy.AI_ALLOWED)
        val projects = listOf(local, allowed).associateBy { it.id }
        val spy = SpyProvider()
        val gw = AiGateway(spy, { true }, { projects[it] })
        // Mixed-project request containing one LOCAL_ONLY record is refused.
        val mixed = ContextPacket("summarize", listOf(
            ContextItem("a", allowed.id, "evidence", "ok"), ContextItem("b", local.id, "evidence", "secret")))
        assertIs<AiResult.Denied>(gw.request(mixed))
        assertIs<AiResult.Denied>(gw.request(ContextPacket("x", listOf(ContextItem("b", local.id, "evidence", "secret")))))
        // Global switch off denies even AI_ALLOWED projects.
        val off = AiGateway(spy, { false }, { projects[it] })
        assertIs<AiResult.Denied>(off.request(ContextPacket("x", listOf(ContextItem("a", allowed.id, "e", "ok")))))
        assertEquals(0, spy.calls)
    }

    @Test
    fun noProviderConfiguredIsHonestlyUnavailable() = runTest {
        val p = sample().project.copy(privacyPolicy = PrivacyPolicy.AI_ALLOWED)
        val gw = AiGateway(null, { true }, { if (it == p.id) p else null })
        assertIs<AiResult.Unavailable>(gw.request(ContextPacket("x", listOf(ContextItem("a", p.id, "e", "ok")))))
    }

    // K. Model failure
    @Test
    fun inventedIdsTimeoutsAndErrorsFailHonestly() = runTest {
        val p = sample().project.copy(privacyPolicy = PrivacyPolicy.AI_ALLOWED)
        val lookup = { id: String -> if (id == p.id) p else null }
        val packet = ContextPacket("x", listOf(ContextItem("real", p.id, "e", "ok")))

        val inventing = AiGateway(SpyProvider { listOf(AiSuggestion("cause", listOf("real", "made-up"))) }, { true }, lookup)
        val r1 = inventing.request(packet)
        assertIs<AiResult.Failed>(r1); assertTrue("made-up" in r1.reason)

        val slow = AiGateway(SpyProvider { delay(10_000); emptyList() }, { true }, lookup, timeoutMs = 100)
        assertIs<AiResult.Failed>(slow.request(packet))

        val broken = AiGateway(SpyProvider { error("malformed json") }, { true }, lookup)
        assertIs<AiResult.Failed>(broken.request(packet))

        val good = AiGateway(SpyProvider { listOf(AiSuggestion("ok", listOf("real"))) }, { true }, lookup)
        val ok = good.request(packet)
        assertIs<AiResult.Ok>(ok); assertNotNull(ok.suggestions.single())
    }
}
