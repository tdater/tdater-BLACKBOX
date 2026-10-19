package com.tdater.blackbox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tdater.blackbox.core.templates.ProjectTemplates
import com.tdater.blackbox.data.BlackboxRepository
import com.tdater.blackbox.system.QuickCapture
import com.tdater.blackbox.ui.MainViewModel
import com.tdater.blackbox.ui.components.BbTopBar
import com.tdater.blackbox.ui.components.InfoCard
import com.tdater.blackbox.ui.components.SectionHeader
import com.tdater.blackbox.ui.components.Tag
import com.tdater.blackbox.ui.components.Tone
import com.tdater.blackbox.ui.components.fmtTime

/** Where should this quick capture go? Last-used project first; an Inbox is always one tap away. */
@Composable
fun QuickCaptureScreen(vm: MainViewModel, mode: String, back: () -> Unit, go: (String, String) -> Unit) {
    val projects by remember { vm.repo.observeProjects() }.collectAsStateWithLifecycle(initialValue = null)
    val what = when (mode) {
        QuickCapture.PHOTO -> "photo"
        QuickCapture.VOICE -> "voice note"
        QuickCapture.READING -> "reading"
        QuickCapture.SCAN -> "code scan"
        else -> "note"
    }
    fun choose(pid: String) { vm.settings.lastProjectId = pid; go(pid, mode) }

    Scaffold(topBar = { BbTopBar("Quick $what", "Where should it go?", onBack = back) }) { pad ->
        val list = projects?.filter { !it.isSample } ?: return@Scaffold
        val last = vm.settings.lastProjectId
        val ordered = list.sortedWith(compareByDescending<com.tdater.blackbox.core.model.Project> { it.id == last }.thenByDescending { it.updatedAt })
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp).widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (list.none { it.title == BlackboxRepository.INBOX_TITLE }) OutlinedButton(onClick = {
                vm.launch { choose(vm.repo.ensureInbox()) }
            }) { Text("📥  Inbox (sort it out later)") }
            if (ordered.isNotEmpty()) SectionHeader("Projects")
            ordered.forEach { p ->
                val t = ProjectTemplates.byId(p.templateId)
                InfoCard(Modifier.clickable { choose(p.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(if (p.title == BlackboxRepository.INBOX_TITLE) "📥" else t.emoji, Color(t.accent))
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(p.title, style = MaterialTheme.typography.titleMedium)
                            Text("updated ${fmtTime(p.updatedAt)}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (p.id == last) Tag("LAST USED", Tone.INFO)
                    }
                }
            }
        }
    }
}
