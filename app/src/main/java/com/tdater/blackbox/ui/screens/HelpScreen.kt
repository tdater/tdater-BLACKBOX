package com.tdater.blackbox.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.tdater.blackbox.ui.components.BbTopBar
import com.tdater.blackbox.ui.components.InfoCard
import kotlinx.coroutines.launch

/** One block of help text. Steps render numbered, examples in monospace. */
sealed interface HelpBlock {
    data class Para(val text: String) : HelpBlock
    data class Steps(val items: List<String>) : HelpBlock
    data class Bullets(val items: List<String>) : HelpBlock
    data class Example(val text: String) : HelpBlock
    data class QA(val q: String, val a: String) : HelpBlock
}

data class HelpTopic(val title: String, val summary: String, val blocks: List<HelpBlock>)

/** In-app guide. Static and offline; keep it in sync with the screens it describes. */
object HelpContent {
    private fun p(t: String) = HelpBlock.Para(t)
    private fun steps(vararg s: String) = HelpBlock.Steps(s.toList())
    private fun bullets(vararg s: String) = HelpBlock.Bullets(s.toList())
    private fun ex(t: String) = HelpBlock.Example(t.trimIndent())
    private fun qa(q: String, a: String) = HelpBlock.QA(q, a)

    val topics = listOf(
        HelpTopic("What BLACKBOX is for", "A flight recorder for debugging and building things.", listOf(
            p("BLACKBOX keeps a structured record of an investigation: what you saw, what you changed, what you tested and what happened. " +
                "It is for bugs and experiments that span days, builds and devices, where memory and scattered screenshots stop being reliable."),
            p("It answers three questions fast: what is actually known, what changed between a good run and a bad one, and where you left off."),
            bullets(
                "Everything stays on this phone. No account, no network permission, no cloud backup.",
                "Observed facts, guesses and tested results are kept apart, so a hunch never quietly turns into a \"fact\".",
                "\"Unknown\" is recorded as unknown. A blank field is never treated as \"unchanged\".",
            ),
        )),
        HelpTopic("Quick start (5 minutes)", "From nothing to a recorded test run.", listOf(
            steps(
                "On Projects, tap New project. Name it after the thing you are working on (an app, a car, a device).",
                "Tap Start investigation and describe one specific problem, e.g. \"OBD values freeze after screen-off\".",
                "Capture what you see: a note, a log, a screenshot, a photo or a voice memo.",
                "Open the Tests tab, tap + New plan, write the steps and the expected result.",
                "Tap Start run, follow the steps, tap Mark when something happens, then pick an outcome and confirm.",
                "Change one thing (a fix, a setting, a build) and run the same plan again. Compare runs shows what differed.",
            ),
            p("Want to look around first? On an empty Projects screen tap Load labeled sample. It is synthetic data, clearly marked, and safe to delete."),
        )),
        HelpTopic("Projects and investigations", "How records are organised.", listOf(
            p("A project is a long-lived thing you work on. An investigation is one problem inside it, with a problem statement, a desired outcome and a status (open, testing, blocked, resolved, archived)."),
            p("On a wide screen (Fold unfolded, tablet, landscape) investigations sit on the left and the workbench on the right. On a narrow screen tap an investigation to open it; Back returns to the list."),
            p("The workbench tabs: Overview (status, next action, resume summary), Evidence, Tests, Claims and Timeline."),
        )),
        HelpTopic("Capturing evidence", "Notes, logs, photos, files, voice.", listOf(
            bullets(
                "Text, log and measurement entries are typed or pasted in Capture.",
                "Take photo uses your camera app. Import screenshot / log / file copies the file into BLACKBOX's private storage, so it survives the original being moved or deleted.",
                "Record voice note needs the microphone only when you tap it. Denying it leaves everything else working.",
                "Share from another app (a screenshot, a log, text) into BLACKBOX and pick where it goes.",
                "Capture during a run attaches the evidence to that run.",
            ),
            p("Deleting evidence withdraws it: the file is removed, the record stays, and any claim that cited it is flagged so it can't quietly keep saying \"supported\"."),
        )),
        HelpTopic("Test plans and runs", "Repeatable checks with a frozen record.", listOf(
            p("A plan has steps, an expected result, what you are changing, what you hold constant and what to measure. Editing a plan saves a new revision; each run keeps a frozen copy of the plan it actually used."),
            steps(
                "Tests tab → Start run. Choose the build and environment snapshot you are testing, and describe the change being tested.",
                "Follow the steps. Tap Mark with a short note whenever something notable happens.",
                "If the plan lists measurements, enter them in the run, e.g. 812 rpm. Numbers and units are compared across runs.",
                "Choose an outcome (PASS, FAIL, PARTIAL, INCONCLUSIVE, ABANDONED) and confirm. Nothing is final until you confirm.",
            ),
            p("Fold half-open (tabletop): the plan stays on the top half and the controls on the bottom. A run in progress survives rotation, folding and app switching, and starting the same plan twice reopens the open run instead of making a duplicate."),
        )),
        HelpTopic("Builds and environments", "Record what was running, where.", listOf(
            p("Project menu (⋮) → Builds & environments. Add a build (name, version, commit, notes) and save environment snapshots: phone, OS, adapter, firmware, connection type, anything that might matter."),
            p("Fill from this phone fills in device fields. Start from latest copies the previous snapshot so you only edit what changed. Any field can be Unknown, and Compare runs reports it as unknown rather than unchanged."),
        )),
        HelpTopic("Compare, resume and fix requests", "Turning records into answers.", listOf(
            bullets(
                "Compare runs: pick two runs. You get every difference in build, environment, plan revision, steps, measurements and outcome, with unknown and missing values called out. Differences are correlation, not proof of cause.",
                "Resume: a short capsule of where things stand: last known-good, last change tested, open questions and the next action, with links to the records it came from. It is marked stale when the investigation changes after it was made.",
                "Fix request: a Markdown handoff for another person or an AI chat. You choose which evidence goes in, edit the text, then copy, save or share it. Attachments are never included automatically.",
            ),
        )),
        HelpTopic("Search and regression checks", "Finding things and not re-breaking them.", listOf(
            p("The magnifying glass on Projects searches every project on the device. Every word must match. Filter by record type or by run outcome; tap FAIL alone to list every failed run. Withdrawn evidence and sample data are hidden unless you turn them on."),
            p("Project menu → Regression checklist lists plans you've marked as regression checks, each with PASSING, FAILING, NEEDS RE-RUN (a newer build exists) or NEVER RUN. Run check starts a run on the newest build and environment."),
            p("Plans that failed and later passed show up as suggestions. Nothing is added to the checklist until you tap Add to checklist."),
        )),
        HelpTopic("Importing logs from your own apps", "Structured event logs (blackbox-events).", listOf(
            p("Apps you build can write a small JSON event log and you import it with Capture → Import app event log (.json). BLACKBOX never listens for events; it only reads files you hand it."),
            ex("""
                {"format":"blackbox-events","version":1,"app":"KITT",
                 "events":[
                  {"t":"2026-10-18T21:00:00Z","level":"info","type":"obd.connect"},
                  {"t":"2026-10-18T21:01:00Z","level":"error","type":"obd.write","message":"Broken pipe"}]}
            """),
            p("One event per line (JSON Lines) also works. Errors, warnings and events with \"mark\": true go on the timeline; everything stays in the log evidence. Times without a time zone are kept as unknown unless the file declares \"timezone\"."),
        )),
        HelpTopic("Backup, archives and privacy", "Your data, your copies.", listOf(
            p("Android backup is turned off for BLACKBOX, so the only copies of your records are the ones you export. Project menu → Export project archive writes a .zip with every record and attachment plus checksums."),
            p("Import project archive (on Projects) always creates a new project, so it can never overwrite existing work. Damaged or tampered archives are rejected."),
            p("Projects default to LOCAL ONLY. No AI provider ships in this build, so nothing is sent anywhere. If one is added later, it will only see records you pick, from projects marked AI ALLOWED, while the global switch in Settings is on."),
        )),
        HelpTopic("Worked example: the frozen OBD gauges", "One investigation, start to finish (it's the sample project).", listOf(
            p("Problem: in a car-diagnostics app, RPM and speed freeze after the screen turns off and back on."),
            steps(
                "Investigation: \"OBD values freeze after screen-off\". Desired outcome: telemetry resumes automatically after screen-on.",
                "Evidence: a pasted logcat excerpt ending in \"IOException: Broken pipe\".",
                "Claim (OBSERVED): \"RPM froze after screen-off on build 1\", citing the log and the failed run.",
                "Claim (INFERRED): \"The Bluetooth socket may be closed by the OS during screen-off\". A guess, labelled as one.",
                "Plan: screen off for 60 s while connected, with an expected result written down before running it.",
                "Run 1 on build 1.4.0 → FAIL. Run 2 on build 1.4.1 → PASS.",
                "Compare runs: the build changed, and Connection is Known for one run but Unknown for the other. That gap is flagged, not hidden: the pass may be down to the fix or to the adapter mode.",
                "Next action recorded: re-run on build 1.4.1 with the adapter in BLE mode. Regression checklist suggests the plan; add it so future builds get checked too.",
            ),
            p("Load labeled sample on an empty Projects screen to walk through this exact example."),
        )),
        HelpTopic("FAQ", "Short answers.", listOf(
            qa("Does BLACKBOX need internet or an account?", "No. It doesn't even request the internet permission."),
            qa("Where are my files stored?", "In BLACKBOX's private app storage. Imported files are copies; deleting the original doesn't affect them."),
            qa("I uninstalled the app. Is my data gone?", "Yes, unless you exported project archives. Backup is disabled on purpose so records don't leave the device silently."),
            qa("Why does Compare say \"unknown\" instead of \"unchanged\"?", "Because nobody recorded that value for one of the runs. Treating a gap as \"same\" is how real causes get missed."),
            qa("What's the difference between observed, inferred and supported?", "Observed: you saw or measured it. Inferred: a reasonable guess. Supported result: backed by a test run. Unknown: not established yet. Accepting a claim marks it reviewed, not proven."),
            qa("I edited a test plan. Did my old runs change?", "No. Each run keeps a frozen copy of the plan revision it used."),
            qa("Can I delete evidence?", "Yes. It is withdrawn: the file goes, the record stays, and claims that relied on it get flagged."),
            qa("Why isn't a plan on my regression checklist automatically?", "A fixed failure is only a suggestion. You decide what is worth re-checking after every change."),
            qa("Is there AI in this?", "Not yet. The privacy rules and refusal paths are built and tested; a provider will be optional and off by default."),
            qa("The microphone or camera was denied. Now what?", "Everything else keeps working. To allow it later: Android Settings → Apps → BLACKBOX → Permissions."),
        )),
    )
}

@Composable
fun HelpScreen(back: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(setOf(0, 1)) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val topics = HelpContent.topics

    Scaffold(topBar = { BbTopBar("Guide", "How to use BLACKBOX", onBack = back) }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(state = listState, modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = 12.dp)) {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        topics.forEachIndexed { i, t ->
                            FilterChip(selected = i in open, onClick = {
                                open = open + i
                                scope.launch { listState.animateScrollToItem(i + 1) }
                            }, label = { Text(t.title) })
                        }
                    }
                }
                itemsIndexed(topics, key = { _, t -> t.title }) { i, t ->
                    val expanded = i in open
                    InfoCard(Modifier.padding(vertical = 4.dp).animateContentSize()) {
                        Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (expanded) "Collapse" else "Expand") {
                            open = if (expanded) open - i else open + i
                        }) {
                            Text((if (expanded) "▾ " else "▸ ") + t.title, style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() })
                            Text(t.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (expanded) Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            t.blocks.forEach { HelpBlockView(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpBlockView(b: HelpBlock) {
    when (b) {
        is HelpBlock.Para -> Text(b.text, style = MaterialTheme.typography.bodyMedium)
        is HelpBlock.Steps -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            b.items.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodyMedium) }
        }
        is HelpBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            b.items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
        is HelpBlock.Example -> InfoCard {
            Text(b.text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                modifier = Modifier.horizontalScroll(rememberScrollState()))
        }
        is HelpBlock.QA -> Column {
            Text(b.q, style = MaterialTheme.typography.titleSmall)
            Text(b.a, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
