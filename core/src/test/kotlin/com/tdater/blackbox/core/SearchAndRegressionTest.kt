package com.tdater.blackbox.core

import com.tdater.blackbox.core.model.BuildReference
import com.tdater.blackbox.core.model.RunOutcome
import com.tdater.blackbox.core.regression.RegressionChecklistBuilder
import com.tdater.blackbox.core.regression.RegressionStatus
import com.tdater.blackbox.core.sample.SampleProject
import com.tdater.blackbox.core.search.HitKind
import com.tdater.blackbox.core.search.RecordSearch
import com.tdater.blackbox.core.search.SearchFilter
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchAndRegressionTest {
    private val now = 1_760_000_000_000L
    private fun id() = UUID.randomUUID().toString()
    private val sample = SampleProject.build(::id, now)

    @Test
    fun allTermsMustMatchCaseInsensitively() {
        val hits = RecordSearch.search(listOf(sample), SearchFilter("BROKEN pipe"))
        assertEquals(listOf(HitKind.EVIDENCE), hits.map { it.kind })
        assertTrue("Broken pipe" in hits[0].snippet)
        assertTrue(RecordSearch.search(listOf(sample), SearchFilter("broken nonexistentword")).isEmpty())
    }

    @Test
    fun kindAndOutcomeFiltersApply() {
        val fails = RecordSearch.search(listOf(sample), SearchFilter(outcomes = setOf(RunOutcome.FAIL)))
        assertEquals(1, fails.size)
        assertEquals(RunOutcome.FAIL, fails[0].outcome)
        val plansOnly = RecordSearch.search(listOf(sample), SearchFilter("screen", kinds = setOf(HitKind.PLAN)))
        assertTrue(plansOnly.isNotEmpty() && plansOnly.all { it.kind == HitKind.PLAN })
        assertTrue(RecordSearch.search(listOf(sample), SearchFilter()).isEmpty())
    }

    @Test
    fun withdrawnAndSampleRecordsAreHiddenUnlessAsked() {
        val log = sample.evidence.first { "Broken pipe" in it.text }
        val b = sample.copy(evidence = sample.evidence.map { if (it.id == log.id) it.copy(withdrawnAt = now) else it })
        assertTrue(RecordSearch.search(listOf(b), SearchFilter("broken pipe")).isEmpty())
        val shown = RecordSearch.search(listOf(b), SearchFilter("broken pipe", includeWithdrawn = true))
        assertTrue(shown.single().withdrawn)
        assertTrue(RecordSearch.search(listOf(sample), SearchFilter("rpm", includeSamples = false)).isEmpty())
    }

    @Test
    fun failThenPassIsOnlySuggestedNeverAutoAdded() {
        val c = RegressionChecklistBuilder.build(sample)
        assertTrue(c.items.isEmpty())
        val s = c.suggestions.single()
        assertEquals(RunOutcome.FAIL, sample.runs.first { it.id == s.failedRunId }.outcome)
        assertEquals(RunOutcome.PASS, sample.runs.first { it.id == s.passedRunId }.outcome)
    }

    @Test
    fun regressionStatusTracksNewestBuild() {
        val flagged = sample.copy(plans = sample.plans.map { it.copy(isRegression = true) })
        // Latest run passed on build 2, which is the newest build.
        assertEquals(RegressionStatus.PASSING, RegressionChecklistBuilder.build(flagged).items.single().status)
        assertTrue(RegressionChecklistBuilder.build(flagged).suggestions.isEmpty())
        // A newer build arrives: the pass no longer covers it.
        val b3 = BuildReference(id(), sample.project.id, "Build 3", "1.4.2", 142, null, "test", now + 1)
        val withNew = flagged.copy(builds = flagged.builds + b3)
        assertEquals(RegressionStatus.NEEDS_RERUN, RegressionChecklistBuilder.build(withNew).items.single().status)
        // No runs at all.
        assertEquals(RegressionStatus.NEVER_RUN, RegressionChecklistBuilder.build(flagged.copy(runs = emptyList())).items.single().status)
        // Latest run failed.
        val lastFail = flagged.copy(runs = flagged.runs.filter { it.outcome == RunOutcome.FAIL })
        assertEquals(RegressionStatus.FAILING, RegressionChecklistBuilder.build(lastFail).items.single().status)
    }
}
