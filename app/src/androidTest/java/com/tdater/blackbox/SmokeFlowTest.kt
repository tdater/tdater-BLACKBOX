package com.tdater.blackbox

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.hasSetTextAction
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
        rule.onNodeWithContentDescription("Project actions").performClick()
        rule.onNodeWithText("Regression checklist").performClick()
        waitText("Suggested from fixed failures")
        rule.onNodeWithText("Add to checklist").performClick()
        waitText("Checks (", substring = true)
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

    private fun waitText(text: String, substring: Boolean = false) {
        rule.waitUntil(15_000) { rule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }
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
