package com.tdater.blackbox.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tdater.blackbox.core.handoff.HandoffGenerator
import com.tdater.blackbox.core.handoff.HandoffOptions
import com.tdater.blackbox.core.handoff.SensitiveScanner
import com.tdater.blackbox.core.model.HandoffDraft
import com.tdater.blackbox.core.model.HandoffReview
import com.tdater.blackbox.core.model.ProjectBundle
import com.tdater.blackbox.ui.MainViewModel
import com.tdater.blackbox.ui.components.BbTopBar
import com.tdater.blackbox.ui.components.InfoCard
import com.tdater.blackbox.ui.components.SectionHeader
import com.tdater.blackbox.ui.components.Tag
import com.tdater.blackbox.ui.components.Tone

/**
 * Fix-request builder: choose records, review sensitive content, edit the Markdown, then copy,
 * save or share through a deliberate action. Nothing is sent automatically.
 */
@Composable
fun HandoffScreen(vm: MainViewModel, pid: String, iid: String, back: () -> Unit) {
    val bundle by rememberBundle(vm, pid)
    val ctx = LocalContext.current
    var audience by rememberSaveable { mutableStateOf("Developer") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var included by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var text by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewed by rememberSaveable { mutableStateOf(false) }
    val b = bundle

    val saveMd = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val content = text ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        vm.launch("Save failed") {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) } ?: error("Could not open destination")
            vm.message = "Saved Markdown. Attachments were not included."
        }
    }

    Scaffold(topBar = { BbTopBar("Fix request", b?.investigations?.firstOrNull { it.id == iid }?.title, onBack = back) }) { pad ->
        if (b == null) return@Scaffold
        val evidence = b.evidence.filter { it.investigationId == iid && it.withdrawnAt == null }
        val inc = included ?: evidence.filter { it.attachmentPath == null }.map { it.id }
        fun regenerate(): String = HandoffGenerator.generate(b, iid, HandoffOptions(audience, inc.toSet(), expanded)).markdown
        val md = text ?: regenerate()
        val warnings = SensitiveScanner.scan(md)

        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader("Audience & detail")
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                listOf("Developer", "Claude Code", "Future me").forEach { a ->
                    FilterChip(selected = audience == a, onClick = { audience = a; text = null; reviewed = false },
                        label = { Text(a) }, modifier = Modifier.padding(end = 6.dp))
                }
                FilterChip(selected = expanded, onClick = { expanded = !expanded; text = null; reviewed = false },
                    label = { Text("Expanded detail") })
            }
            SectionHeader("Included evidence (${inc.size}/${evidence.size})")
            Text("Only checked records appear in the text. Attachments are referenced by name and checksum; the files themselves are not transferred.",
                style = MaterialTheme.typography.labelSmall)
            evidence.forEach { e ->
                Row(Modifier.fillMaxWidth().clickable {
                    included = if (e.id in inc) inc - e.id else inc + e.id; text = null; reviewed = false
                }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = e.id in inc, onCheckedChange = null)
                    Text("${e.kind.name} · ${e.title}" + if (e.attachmentPath != null) " (attachment)" else "", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (warnings.isNotEmpty()) InfoCard {
                Tag("REVIEW BEFORE SHARING", Tone.WARN)
                Text("Possible sensitive content: " + warnings.joinToString(), style = MaterialTheme.typography.bodySmall)
                Text("Detection is heuristic and can miss things. Edit the text below or exclude records. Follow your employer's policy for work data.",
                    style = MaterialTheme.typography.labelSmall)
            }
            SectionHeader("Preview (editable)")
            OutlinedTextField(value = md, onValueChange = { text = it; reviewed = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp))
            OutlinedButton(onClick = { text = null; reviewed = false }) { Text("Regenerate from records (discards edits)") }
            Row(Modifier.fillMaxWidth().clickable { reviewed = !reviewed }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = reviewed, onCheckedChange = { reviewed = it })
                Text("I reviewed the included content", style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = reviewed, onClick = {
                    record(vm, b, iid, inc, evidence.map { it.id }, md)
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("BLACKBOX fix request", md))
                    vm.message = "Copied. Attachments are not included in copied text."
                }) { Text("Copy Markdown") }
                OutlinedButton(enabled = reviewed, onClick = {
                    record(vm, b, iid, inc, evidence.map { it.id }, md)
                    saveMd.launch("fix-request.md")
                }) { Text("Save .md") }
                OutlinedButton(enabled = reviewed, onClick = {
                    record(vm, b, iid, inc, evidence.map { it.id }, md)
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, md)
                    ctx.startActivity(Intent.createChooser(send, "Share fix request"))
                }) { Text("Share…") }
            }
        }
    }
}

private fun record(vm: MainViewModel, b: ProjectBundle, iid: String, inc: List<String>, all: List<String>, md: String) {
    val sources = HandoffGenerator.generate(b, iid, HandoffOptions(includedEvidenceIds = inc.toSet())).sourceRecordIds
    vm.launch {
        vm.repo.saveHandoff(HandoffDraft(vm.repo.newId(), b.project.id, iid, sources, inc, all - inc.toSet(),
            System.currentTimeMillis(), "markdown", md, HandoffReview.REVIEWED))
    }
}
