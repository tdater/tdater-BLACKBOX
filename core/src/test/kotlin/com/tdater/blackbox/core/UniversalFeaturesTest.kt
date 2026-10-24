package com.tdater.blackbox.core

import com.tdater.blackbox.core.model.Evidence
import com.tdater.blackbox.core.model.EvidenceKind
import com.tdater.blackbox.core.model.Investigation
import com.tdater.blackbox.core.model.Measurement
import com.tdater.blackbox.core.model.PlanSnapshot
import com.tdater.blackbox.core.model.Project
import com.tdater.blackbox.core.model.ProjectBundle
import com.tdater.blackbox.core.model.RunMarker
import com.tdater.blackbox.core.model.RunOutcome
import com.tdater.blackbox.core.model.TestPlan
import com.tdater.blackbox.core.model.TestRun
import com.tdater.blackbox.core.procedures.ProcedureLibrary
import com.tdater.blackbox.core.readings.Readings
import com.tdater.blackbox.core.report.ReportBuilder
import com.tdater.blackbox.core.sample.SampleProject
import com.tdater.blackbox.core.search.HitKind
import com.tdater.blackbox.core.search.SimilarRecords
import com.tdater.blackbox.core.templates.ProjectTemplates
import org.junit.Test
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UniversalFeaturesTest {
    private val now = 1_760_000_000_000L
    private fun id() = UUID.randomUUID().toString()

    @Test
    fun templatesAreUniqueAndCustomIsTheFallback() {
        val ids = ProjectTemplates.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(ProjectTemplates.CUSTOM, ProjectTemplates.byId("nope").id)
        assertEquals("vehicle", ProjectTemplates.byId("vehicle").id)
        assertTrue(ProjectTemplates.all.filter { it.id != ProjectTemplates.CUSTOM }.all { it.fields.isNotEmpty() && it.exampleProblems.isNotEmpty() })
    }

    @Test
    fun readingValuesParseUnitsAndDecimalStyles() {
        assertEquals(12.4 to "V", Readings.parseValue("12.4 V"))
        assertEquals(12.4 to "V", Readings.parseValue("12.4V"))
        assertEquals(-3.5 to "°C", Readings.parseValue("-3,5 °C"))
        assertEquals(1234.5 to "mi", Readings.parseValue("1,234.5 mi"))
        assertEquals(98.6 to null, Readings.parseValue("98.6"))
        assertNull(Readings.parseValue("about twelve volts"))
        assertEquals(Triple("Voltage", 12.6, "V"), Readings.parseLine("Voltage: 12.6 V", "ignored"))
        assertEquals(Triple("Battery", 12.6, "V"), Readings.parseLine("12.6 V", "Battery"))
        assertNull(Readings.parseLine("12.6 V", " "))
    }

    private fun bundleWithReadings(): ProjectBundle {
        val p = Project("p", "Truck", createdAt = now, updatedAt = now, templateId = "vehicle")
        val inv = Investigation("i", "p", "Battery dies", createdAt = now, updatedAt = now)
        fun ev(t: Long, title: String, text: String, withdrawn: Long? = null) =
            Evidence(id(), "p", "i", kind = EvidenceKind.MEASUREMENT, title = title, text = text, capturedAt = t, withdrawnAt = withdrawn)
        val snap = PlanSnapshot("plan", 1, "Overnight test", listOf("Park", "Measure"), "12.4 V or more")
        val run = TestRun("r", "p", "i", "plan", snap, startedAt = now + 5_000, endedAt = now + 6_000, outcome = RunOutcome.FAIL,
            measurements = listOf(Measurement("battery voltage", 11.9, "V"), Measurement("Current", null, "A")))
        return ProjectBundle(p, listOf(inv), runs = listOf(run), evidence = listOf(
            ev(now + 1_000, "Battery voltage", "12.6 V"),
            ev(now + 2_000, "Note", "Battery voltage: 12.2 V"),
            ev(now + 3_000, "Battery voltage", "not a number"),
            ev(now + 4_000, "Battery voltage", "1 V", withdrawn = now),
        ))
    }

    @Test
    fun readingsGroupRunsAndLoggedValuesAndSkipWithdrawnOrUnparsed() {
        val series = Readings.collect(bundleWithReadings())
        assertEquals(1, series.size)
        val s = series.single()
        assertEquals(listOf(12.6, 12.2, 11.9), s.points.map { it.value })
        assertEquals("V", s.unit)
        assertEquals(listOf(false, false, true), s.points.map { it.fromRun })
        assertTrue(Readings.describe(s).startsWith("3 readings, down 0.7 V"))
    }

    @Test
    fun similarRecordsMatchOnDistinctiveWordsButNeverTheSameInvestigation() {
        val sample = SampleProject.build(::id, now)
        val other = ProjectBundle(
            Project("q", "Car", createdAt = now, updatedAt = now),
            listOf(Investigation("j", "q", "Phone loses OBD adapter", createdAt = now, updatedAt = now)),
            evidence = listOf(Evidence("e1", "q", "j", kind = EvidenceKind.LOG, title = "adapter log",
                text = "IOException: Broken pipe from OBD adapter after screen off", capturedAt = now)),
        )
        val sampleInv = sample.investigations.first().id
        val hits = SimilarRecords.find("IOException Broken pipe adapter", listOf(sample, other), includeSamples = true,
            excludeInvestigationId = sampleInv)
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.none { it.investigationId == sampleInv })
        assertEquals("e1", hits.first().id)
        assertEquals(HitKind.EVIDENCE, hits.first().kind)
        assertTrue("ioexception" in hits.first().sharedTerms)
        // Samples are excluded unless asked; common words alone never match.
        assertTrue(SimilarRecords.find("the and with after", listOf(sample, other)).isEmpty())
        assertTrue(SimilarRecords.find("IOException Broken pipe", listOf(sample)).isEmpty())
    }

    @Test
    fun reportListsWhatWasRecordedAndMarksWithdrawn() {
        val b = bundleWithReadings()
        val r = ReportBuilder.build(b, "i", ZoneOffset.UTC)
        assertEquals("Battery dies", r.title)
        assertEquals(listOf("Summary", "Tests", "Readings", "Evidence"), r.sections.map { it.heading })
        assertTrue(r.sections.first().bullets.any { it == "Problem: not recorded" })
        assertTrue(r.sections.first { it.heading == "Tests" }.bullets.single().contains("FAIL"))
        assertTrue(r.sections.first { it.heading == "Evidence" }.bullets.any { it.endsWith("(withdrawn)") })
        assertTrue(ReportBuilder.markdown(r).startsWith("# Battery dies"))
    }

    @Test
    fun proceduresRoundTripAndBecomeFreshPlans() {
        val plan = TestPlan("plan", "p", "i", "Overnight battery test", steps = listOf("Charge", "Park 12 h", "Measure"),
            expectedResult = "12.4 V or more", createdAt = now, updatedAt = now)
        val proc = ProcedureLibrary.fromPlan(plan, "vehicle", "proc", now)
        assertEquals(listOf(proc), ProcedureLibrary.decode(ProcedureLibrary.encode(listOf(proc))))
        val copy = ProcedureLibrary.toPlan(proc, "p2", "i2", "plan2", now)
        assertEquals(plan.steps, copy.steps)
        assertEquals("i2", copy.investigationId)
        assertEquals(1, copy.revision)
    }

    @Test
    fun latestMarkerDecidesWhetherAStepIsTicked() {
        val steps = listOf("Charge", "Park", "Measure")
        val markers = listOf(
            RunMarker(1, ProcedureLibrary.tickNote(0, steps[0], true)),
            RunMarker(2, ProcedureLibrary.tickNote(1, steps[1], true)),
            RunMarker(3, "free text marker"),
            RunMarker(4, ProcedureLibrary.tickNote(1, steps[1], false)),
        )
        assertEquals(setOf(0), ProcedureLibrary.tickedSteps(markers))
        val run = TestRun("r", "p", "i", "plan", PlanSnapshot("plan", 1, "t", steps, "x"), startedAt = 0, markers = markers)
        assertEquals(1 to 3, ProcedureLibrary.progress(run))
    }
}
