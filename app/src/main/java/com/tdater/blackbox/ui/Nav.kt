package com.tdater.blackbox.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tdater.blackbox.core.search.HitKind
import com.tdater.blackbox.ui.screens.AiScreen
import com.tdater.blackbox.ui.screens.CaptureScreen
import com.tdater.blackbox.ui.screens.ChecklistsScreen
import com.tdater.blackbox.ui.screens.QuickCaptureScreen
import com.tdater.blackbox.ui.screens.ReadingsScreen
import com.tdater.blackbox.ui.screens.CompareScreen
import com.tdater.blackbox.ui.screens.ConfigScreen
import com.tdater.blackbox.ui.screens.EvidenceScreen
import com.tdater.blackbox.ui.screens.HandoffScreen
import com.tdater.blackbox.ui.screens.HelpScreen
import com.tdater.blackbox.ui.screens.PlanEditorScreen
import com.tdater.blackbox.ui.screens.ProjectWorkspace
import com.tdater.blackbox.ui.screens.ProjectsScreen
import com.tdater.blackbox.ui.screens.RegressionScreen
import com.tdater.blackbox.ui.screens.ResumeScreen
import com.tdater.blackbox.ui.screens.RunScreen
import com.tdater.blackbox.ui.screens.SearchScreen
import com.tdater.blackbox.ui.screens.SettingsScreen

/** Route builders. Optional ids are passed as query parameters. */
object Routes {
    const val PROJECTS = "projects"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val HELP = "help"
    const val CHECKLISTS = "checklists"
    fun quick(mode: String) = "quick/$mode"
    fun project(pid: String, iid: String? = null) = "project/$pid" + (iid?.let { "?iid=$it" } ?: "")
    fun capture(pid: String, iid: String?, runId: String? = null, mode: String? = null) =
        "capture/$pid?iid=${iid.orEmpty()}&run=${runId.orEmpty()}&mode=${mode.orEmpty()}"
    fun readings(pid: String, iid: String? = null) = "readings/$pid?iid=${iid.orEmpty()}"
    fun plan(pid: String, iid: String, planId: String? = null) = "plan/$pid/$iid?plan=${planId.orEmpty()}"
    fun run(pid: String, runId: String) = "run/$pid/$runId"
    fun compare(pid: String, iid: String) = "compare/$pid/$iid"
    fun resume(pid: String, iid: String) = "resume/$pid/$iid"
    fun handoff(pid: String, iid: String) = "handoff/$pid/$iid"
    fun config(pid: String) = "config/$pid"
    fun evidence(pid: String, eid: String) = "evidence/$pid/$eid"
    fun regression(pid: String) = "regression/$pid"
    fun ai(pid: String, iid: String) = "ai/$pid/$iid"
}

private fun opt(name: String) = navArgument(name) { type = NavType.StringType; nullable = true; defaultValue = null }

@Composable
fun BlackboxNav(vm: MainViewModel, nav: NavHostController = rememberNavController()) {
    val back: () -> Unit = { nav.popBackStack() }
    // Widget / tile / notification taps arrive as pending requests; act on them once the graph exists.
    LaunchedEffect(vm.pendingQuick) {
        vm.pendingQuick?.let { mode -> vm.pendingQuick = null; nav.navigate(Routes.quick(mode)) { launchSingleTop = true } }
    }
    LaunchedEffect(vm.pendingOpen) {
        vm.pendingOpen?.let { (pid, iid) -> vm.pendingOpen = null; nav.navigate(Routes.project(pid, iid)) { popUpTo(Routes.PROJECTS) } }
    }
    NavHost(nav, startDestination = Routes.PROJECTS) {
        composable(Routes.PROJECTS) {
            ProjectsScreen(vm, open = { pid, iid -> nav.navigate(Routes.project(pid, iid)) }, openSettings = { nav.navigate(Routes.SETTINGS) },
                openSearch = { nav.navigate(Routes.SEARCH) }, openHelp = { nav.navigate(Routes.HELP) },
                quick = { nav.navigate(Routes.quick(it)) }, openChecklists = { nav.navigate(Routes.CHECKLISTS) })
        }
        composable("quick/{mode}") { e ->
            QuickCaptureScreen(vm, e.arguments?.getString("mode") ?: "note", back) { pid, mode ->
                nav.navigate(Routes.capture(pid, null, null, mode)) { popUpTo("quick/{mode}") { inclusive = true } }
            }
        }
        composable(Routes.CHECKLISTS) {
            ChecklistsScreen(vm, back, openInvestigation = { pid, iid -> nav.navigate(Routes.project(pid, iid)) })
        }
        composable("readings/{pid}?iid={iid}", listOf(navArgument("pid") {}, opt("iid"))) { e ->
            ReadingsScreen(vm, e.arguments!!.getString("pid")!!, e.arguments?.getString("iid")?.ifBlank { null }, back)
        }
        composable(Routes.SETTINGS) { SettingsScreen(vm, back, openHelp = { nav.navigate(Routes.HELP) }, openProject = { nav.navigate(Routes.project(it)) { popUpTo(Routes.PROJECTS) } }) }
        composable(Routes.HELP) { HelpScreen(back) }
        composable(Routes.SEARCH) {
            SearchScreen(vm, back, openHit = { h ->
                nav.navigate(when (h.kind) {
                    HitKind.EVIDENCE -> Routes.evidence(h.projectId, h.id)
                    HitKind.RUN -> Routes.run(h.projectId, h.id)
                    HitKind.PLAN -> h.investigationId?.let { Routes.plan(h.projectId, it, h.id) } ?: Routes.project(h.projectId)
                    HitKind.BUILD, HitKind.ENVIRONMENT -> Routes.config(h.projectId)
                    HitKind.PROJECT, HitKind.INVESTIGATION, HitKind.CLAIM -> Routes.project(h.projectId, h.investigationId)
                })
            })
        }
        composable("regression/{pid}") { e ->
            val pid = e.arguments!!.getString("pid")!!
            RegressionScreen(vm, pid, back, openRun = { nav.navigate(Routes.run(pid, it)) })
        }
        composable("project/{pid}?iid={iid}", listOf(navArgument("pid") {}, opt("iid"))) { e ->
            val pid = e.arguments!!.getString("pid")!!
            ProjectWorkspace(vm, pid, e.arguments?.getString("iid"), nav, back)
        }
        composable("capture/{pid}?iid={iid}&run={run}&mode={mode}", listOf(navArgument("pid") {}, opt("iid"), opt("run"), opt("mode"))) { e ->
            CaptureScreen(vm, e.arguments!!.getString("pid")!!, e.arguments?.getString("iid")?.ifBlank { null },
                e.arguments?.getString("run")?.ifBlank { null }, back, e.arguments?.getString("mode")?.ifBlank { null })
        }
        composable("plan/{pid}/{iid}?plan={plan}", listOf(navArgument("pid") {}, navArgument("iid") {}, opt("plan"))) { e ->
            PlanEditorScreen(vm, e.arguments!!.getString("pid")!!, e.arguments!!.getString("iid")!!,
                e.arguments?.getString("plan")?.ifBlank { null }, back)
        }
        composable("run/{pid}/{runId}") { e ->
            val pid = e.arguments!!.getString("pid")!!
            RunScreen(vm, pid, e.arguments!!.getString("runId")!!, back,
                capture = { iid, runId -> nav.navigate(Routes.capture(pid, iid, runId)) },
                openEvidence = { nav.navigate(Routes.evidence(pid, it)) })
        }
        composable("compare/{pid}/{iid}") { e ->
            CompareScreen(vm, e.arguments!!.getString("pid")!!, e.arguments!!.getString("iid")!!, back)
        }
        composable("resume/{pid}/{iid}") { e ->
            val pid = e.arguments!!.getString("pid")!!
            ResumeScreen(vm, pid, e.arguments!!.getString("iid")!!, back,
                openInvestigation = { nav.navigate(Routes.project(pid, it)) { popUpTo(Routes.PROJECTS) } })
        }
        composable("handoff/{pid}/{iid}") { e ->
            HandoffScreen(vm, e.arguments!!.getString("pid")!!, e.arguments!!.getString("iid")!!, back)
        }
        composable("ai/{pid}/{iid}") { e ->
            AiScreen(vm, e.arguments!!.getString("pid")!!, e.arguments!!.getString("iid")!!, back,
                openSettings = { nav.navigate(Routes.SETTINGS) })
        }
        composable("config/{pid}") { e -> ConfigScreen(vm, e.arguments!!.getString("pid")!!, back) }
        composable("evidence/{pid}/{eid}") { e ->
            EvidenceScreen(vm, e.arguments!!.getString("pid")!!, e.arguments!!.getString("eid")!!, back,
                openHit = { pid, iid, eid -> nav.navigate(if (eid != null) Routes.evidence(pid, eid) else Routes.project(pid, iid)) })
        }
    }
}
