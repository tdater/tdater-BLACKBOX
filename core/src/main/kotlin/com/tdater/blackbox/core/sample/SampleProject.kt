package com.tdater.blackbox.core.sample

import com.tdater.blackbox.core.model.BuildReference
import com.tdater.blackbox.core.model.Claim
import com.tdater.blackbox.core.model.ClaimClassification
import com.tdater.blackbox.core.model.EnvField
import com.tdater.blackbox.core.model.EnvValue
import com.tdater.blackbox.core.model.EnvironmentSnapshot
import com.tdater.blackbox.core.model.Evidence
import com.tdater.blackbox.core.model.EvidenceKind
import com.tdater.blackbox.core.model.Investigation
import com.tdater.blackbox.core.model.InvestigationStatus
import com.tdater.blackbox.core.model.Measurement
import com.tdater.blackbox.core.model.PlanSnapshot
import com.tdater.blackbox.core.model.Project
import com.tdater.blackbox.core.model.ProjectBundle
import com.tdater.blackbox.core.model.ProjectCategory
import com.tdater.blackbox.core.model.RunOutcome
import com.tdater.blackbox.core.model.TestPlan
import com.tdater.blackbox.core.model.TestRun
import com.tdater.blackbox.core.model.TimelineEvent
import com.tdater.blackbox.core.model.TimelineType

/**
 * Clearly-labelled SYNTHETIC demo: OBD values freezing after screen-off.
 * Every value here is invented for demonstration and marked as such.
 */
object SampleProject {
    fun build(newId: () -> String, now: Long): ProjectBundle {
        val pid = newId()
        val invId = newId()
        val planId = newId()
        val b1 = newId(); val b2 = newId()
        val e1 = newId(); val e2 = newId()
        val r1 = newId(); val r2 = newId()
        val ev1 = newId(); val ev2 = newId()
        val hour = 3_600_000L
        val t0 = now - 26 * hour

        val project = Project(pid, "[SAMPLE] OBD freeze after screen-off",
            description = "SYNTHETIC DEMO DATA. Not a real investigation. Safe to delete.",
            category = ProjectCategory.SAMPLE, createdAt = t0, updatedAt = now,
            objective = "Live OBD values keep updating after the screen turns off and back on.", isSample = true)
        val plan = TestPlan(planId, pid, invId, "Screen-off for 60 s while connected",
            objective = "Reproduce the freeze deterministically",
            prerequisites = "Adapter paired, engine running, gauges screen open",
            steps = listOf("Open gauges screen and confirm RPM updates", "Press power to turn screen off",
                "Wait 60 seconds", "Turn screen on and unlock", "Watch RPM for 10 seconds"),
            expectedResult = "RPM resumes updating within 3 seconds of unlock",
            variablesToChange = "Build", variablesToHoldConstant = "Adapter, vehicle, connection mode",
            measurements = listOf("Recovery time"), createdAt = t0, updatedAt = t0)
        val snap = PlanSnapshot(planId, 1, plan.title, plan.steps, plan.expectedResult)
        val inv = Investigation(invId, pid, "OBD values freeze after screen-off",
            problemStatement = "After screen-off and back on, RPM/speed stay frozen until the app is restarted.",
            desiredOutcome = "Telemetry resumes automatically after screen-on.",
            status = InvestigationStatus.TESTING,
            currentQuestion = "Does the Bluetooth socket survive Doze/screen-off on build 2?",
            nextAction = "Run the same plan on build 2 with the adapter in BLE mode.",
            knownGoodReferenceId = r2, createdAt = t0, updatedAt = now, revision = 7)
        fun env(id: String, at: Long, link: EnvValue) = EnvironmentSnapshot(id, pid, at, "sample", listOf(
            EnvField("Device", EnvValue.Known("Sample phone (synthetic)")),
            EnvField("Android", EnvValue.Known("16")),
            EnvField("Adapter", EnvValue.Known("Generic ELM327 (synthetic)")),
            EnvField("Connection", link),
            EnvField("Battery optimization", EnvValue.Known("Optimized")),
        ))
        return ProjectBundle(
            project = project,
            investigations = listOf(inv),
            builds = listOf(
                BuildReference(b1, pid, "Build 1 (synthetic)", "1.4.0", 140, "abc1234", "sample", t0),
                BuildReference(b2, pid, "Build 2 (synthetic)", "1.4.1", 141, "def5678", "sample", t0 + hour),
            ),
            environments = listOf(env(e1, t0 + 2 * hour, EnvValue.Known("Bluetooth Classic")),
                env(e2, t0 + 20 * hour, EnvValue.Unknown("not checked"))),
            plans = listOf(plan),
            runs = listOf(
                TestRun(r1, pid, invId, planId, snap, startedAt = t0 + 2 * hour, endedAt = t0 + 2 * hour + 300_000,
                    environmentSnapshotId = e1, buildReferenceId = b1, intendedChange = "Baseline on build 1",
                    actualResult = "RPM stayed frozen at 812 after unlock; recovered only after app restart.",
                    outcome = RunOutcome.FAIL, measurements = listOf(Measurement("Recovery time", null, "s"))),
                TestRun(r2, pid, invId, planId, snap, startedAt = t0 + 20 * hour, endedAt = t0 + 20 * hour + 290_000,
                    environmentSnapshotId = e2, buildReferenceId = b2,
                    intendedChange = "Reconnect socket on ACTION_SCREEN_ON", actualChanges = "Build 2: reconnect on screen-on",
                    actualResult = "RPM resumed 2 s after unlock.", outcome = RunOutcome.PASS,
                    measurements = listOf(Measurement("Recovery time", 2.0, "s"))),
            ),
            evidence = listOf(
                Evidence(ev1, pid, invId, testRunId = r1, kind = EvidenceKind.LOG, title = "Logcat excerpt (synthetic)",
                    text = "10-18 21:04:11.201 W ObdService: socket read timeout\n10-18 21:04:11.202 E ObdService: IOException: Broken pipe",
                    capturedAt = t0 + 2 * hour + 200_000),
                Evidence(ev2, pid, invId, testRunId = r2, kind = EvidenceKind.TEXT, title = "Note after build 2",
                    text = "Gauges came back on their own. Did not check whether adapter was in BLE mode.",
                    capturedAt = t0 + 20 * hour + 280_000),
            ),
            claims = listOf(
                Claim(newId(), pid, invId, "RPM froze after screen-off on build 1.", ClaimClassification.OBSERVED,
                    evidenceIds = listOf(ev1), testRunIds = listOf(r1), createdAt = t0 + 3 * hour),
                Claim(newId(), pid, invId, "The Bluetooth socket may be closed by the OS during screen-off.",
                    ClaimClassification.INFERRED, evidenceIds = listOf(ev1), createdAt = t0 + 3 * hour),
            ),
            timeline = listOf(
                TimelineEvent(newId(), pid, null, null, t0, null, TimelineType.PROJECT_CREATED, pid, "Sample project created"),
                TimelineEvent(newId(), pid, invId, null, t0 + 2 * hour + 300_000, null, TimelineType.RUN_FINISHED, r1, "Run FAIL on build 1"),
                TimelineEvent(newId(), pid, invId, null, t0 + 20 * hour + 290_000, null, TimelineType.RUN_FINISHED, r2, "Run PASS on build 2"),
            ),
        )
    }
}
