package com.tdater.blackbox

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tdater.blackbox.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end smoke run on an emulator: sample project, workbench, configuration change, search and the
 * regression checklist. Screenshots go to /data/local/tmp/blackbox-shots (pulled by CI) so layouts can be
 * reviewed per device profile.
 */
@RunWith(AndroidJUnit4::class)
class SmokeFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<BlackboxApp>()

    @Before
    fun clean() = runBlocking {
        app.repository.allProjects().forEach { app.repository.deleteProject(it.id) }
    }

    @Test
    fun sampleWorkbenchSearchAndRegression() {
        waitText(LOAD_SAMPLE)
        shot("01-projects-empty")
        waitDesc("Guide and FAQ")
        rule.onNodeWithContentDescription("Guide and FAQ").performClick()
        waitText("Quick start (5 minutes)")
        shot("02-guide")
        back()
        waitText(LOAD_SAMPLE)
        rule.onNodeWithText(LOAD_SAMPLE).performClick()

        waitText(INVESTIGATION)
        shot("02-workspace")
        rule.onAllNodesWithText(INVESTIGATION)[0].performClick()
        waitText("Timeline")
        shot("03-workbench")

        // Rotation / fold / resize: the selected investigation must survive activity recreation.
        rule.activityRule.scenario.recreate()
        waitText("Timeline")
        shot("04-after-recreate")

        backToProjects()
        shot("05-projects")
        rule.onNodeWithContentDescription("Search records").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("broken pipe")
        waitText("1 result(s)", substring = true)
        shot("06-search")

        back()
        waitText(SAMPLE_PROJECT)
        rule.onNodeWithText(SAMPLE_PROJECT).performClick()
        // The project menu only appears once the project bundle has loaded.
        waitDesc("Project actions")
        rule.onNodeWithContentDescription("Project actions").performClick()
        waitText("Regression checklist")
        rule.onNodeWithText("Regression checklist").performClick()
        // SectionHeader renders upper case.
        waitText("Suggested from fixed failures", ignoreCase = true)
        rule.onNodeWithText("Add to checklist").performClick()
        waitText("Checks (", substring = true, ignoreCase = true)
        shot("07-regression")
    }

    private fun backToProjects() {
        repeat(4) {
            if (rule.onAllNodesWithContentDescription("Search records").fetchSemanticsNodes().isNotEmpty()) return
            back()
            rule.waitForIdle()
        }
        rule.onNodeWithContentDescription("Search records").assertExists()
    }

    /** System back through the activity's dispatcher (no Espresso window-focus requirement). */
    private fun back() {
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun waitText(text: String, substring: Boolean = false, ignoreCase: Boolean = false) =
        waitFor("text \"$text\"") { rule.onAllNodes(hasText(text, substring, ignoreCase)).fetchSemanticsNodes().isNotEmpty() }

    private fun waitDesc(desc: String) =
        waitFor("content description \"$desc\"") { rule.onAllNodesWithContentDescription(desc).fetchSemanticsNodes().isNotEmpty() }

    /** Waits, and on timeout fails with what was being waited for plus the current UI tree and a screenshot. */
    private fun waitFor(what: String, condition: () -> Boolean) {
        try {
            rule.waitUntil(15_000, condition)
        } catch (e: ComposeTimeoutException) {
            runCatching { shot("FAIL") }
            val roots = rule.onAllNodes(isRoot())
            val tree = (0 until roots.fetchSemanticsNodes().size).joinToString("\n---\n") { roots[it].printToString(maxDepth = 12) }
            throw AssertionError("Timed out waiting for $what. UI tree:\n$tree", e)
        }
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        val width = app.resources.configuration.screenWidthDp
        sh("mkdir -p $SHOTS")
        sh("screencap -p $SHOTS/w${width}dp-$name.png")
    }

    private fun sh(cmd: String) {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
    }

    private companion object {
        const val SHOTS = "/data/local/tmp/blackbox-shots"
        const val LOAD_SAMPLE = "Load labeled sample (synthetic data)"
        const val SAMPLE_PROJECT = "[SAMPLE] OBD freeze after screen-off"
        const val INVESTIGATION = "OBD values freeze after screen-off"
    }
}
