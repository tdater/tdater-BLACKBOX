package com.tdater.blackbox.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

/** How the app names things. Everyday words for most people; the original technical terms for those who want them. */
enum class Wording(val label: String, val blurb: String) {
    EVERYDAY("Everyday", "Problems, tries, findings, notes"),
    TECHNICAL("Technical", "Investigations, runs, claims, evidence"),
}

data class Lingo(
    val investigation: String,
    val investigations: String,
    val newInvestigation: String,
    val startInvestigation: String,
    val investigationHint: String,
    val plan: String,
    val newPlan: String,
    val run: String,
    val runs: String,
    val startRun: String,
    val claims: String,
    val addClaim: String,
    val evidence: String,
    val buildsAndEnv: String,
    val snapshot: String,
    val regression: String,
    val handoff: String,
    val knownGood: String,
    val compare: String,
    val resume: String,
    val nextAction: String,
    val openCount: String,
) {
    companion object {
        val EVERYDAY = Lingo(
            investigation = "Problem", investigations = "Problems", newInvestigation = "New problem",
            startInvestigation = "Add a problem",
            investigationHint = "Add one problem at a time, like \"Car won't start when it's cold\" or \"Wi‑Fi drops every evening\".",
            plan = "Test", newPlan = "New test", run = "Try", runs = "Tries", startRun = "Start a try",
            claims = "Findings", addClaim = "Add finding", evidence = "Notes & proof",
            buildsAndEnv = "Setup & conditions", snapshot = "Conditions snapshot", regression = "Recheck list",
            handoff = "Share summary", knownGood = "Last time it worked", compare = "Compare tries", resume = "Where was I?",
            nextAction = "Next step", openCount = "OPEN PROBLEMS",
        )
        val TECHNICAL = Lingo(
            investigation = "Investigation", investigations = "Investigations", newInvestigation = "New investigation",
            startInvestigation = "Start investigation",
            investigationHint = "No investigations yet. Start one for a specific problem, e.g. \"OBD values freeze after screen-off\".",
            plan = "Test plan", newPlan = "New test plan", run = "Run", runs = "Runs", startRun = "Start run",
            claims = "Claims", addClaim = "Add claim", evidence = "Evidence",
            buildsAndEnv = "Builds & environments", snapshot = "Environment snapshot", regression = "Regression checklist",
            handoff = "Fix request", knownGood = "Known-good reference", compare = "Compare runs", resume = "Resume",
            nextAction = "Next action", openCount = "OPEN INVESTIGATIONS",
        )
        fun of(w: Wording) = if (w == Wording.TECHNICAL) TECHNICAL else EVERYDAY
    }
}

val LocalLingo = staticCompositionLocalOf { Lingo.EVERYDAY }
