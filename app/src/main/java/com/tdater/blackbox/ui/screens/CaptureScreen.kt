package com.tdater.blackbox.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.tdater.blackbox.core.model.EvidenceKind
import com.tdater.blackbox.core.model.RunOutcome
import com.tdater.blackbox.ui.MainViewModel
import com.tdater.blackbox.ui.RecordingTarget
import com.tdater.blackbox.ui.components.BbTopBar
import com.tdater.blackbox.ui.components.Field
import com.tdater.blackbox.ui.components.InfoCard
import com.tdater.blackbox.ui.components.SectionHeader
import com.tdater.blackbox.ui.components.shortId
import kotlinx.coroutines.delay
import java.io.File

/**
 * Save-first capture. Nothing here waits on network, AI or transcription. The draft and the
 * selected association are saved state, so a fold/rotate never loses typed text.
 */
@Composable
fun CaptureScreen(vm: MainViewModel, pid: String, initialIid: String?, initialRunId: String?, back: () -> Unit) {
    val bundle by rememberBundle(vm, pid)
    val ctx = LocalContext.current
    var iid by rememberSaveable { mutableStateOf(initialIid) }
    var runId by rememberSaveable { mutableStateOf(initialRunId) }
    var kind by rememberSaveable { mutableStateOf(EvidenceKind.TEXT) }
    var title by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var pickInv by rememberSaveable { mutableStateOf(false) }
    var micDenied by rememberSaveable { mutableStateOf(false) }
    val b = bundle

    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        vm.launch("Import failed") {
            uris.forEach { vm.repo.importEvidence(ctx.contentResolver, it, pid, iid, runId) }
            vm.message = "Imported ${uris.size} file(s) into app storage."
        }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingPhoto ?: return@rememberLauncherForActivityResult
        pendingPhoto = null
        val f = File(path)
        if (!ok || !f.exists() || f.length() == 0L) { f.delete(); vm.message = "Photo cancelled; nothing saved."; return@rememberLauncherForActivityResult }
        vm.launch("Saving photo failed") {
            vm.repo.addCapturedFile(f, pid, iid, runId, EvidenceKind.PHOTO, f.name, "image/jpeg", "Photo ${java.time.LocalTime.now().withNano(0)}")
            vm.message = "Photo saved."
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micDenied = !granted
        if (granted) vm.startRecording(RecordingTarget(pid, iid, runId))
    }

    Scaffold(topBar = { BbTopBar("Capture", b?.project?.title, onBack = back) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp).widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Association: always visible and changeable.
            InfoCard {
                val inv = b?.investigations?.firstOrNull { it.id == iid }
                val run = b?.runs?.firstOrNull { it.id == runId }
                Text("Saving to: ${b?.project?.title ?: "…"}", style = MaterialTheme.typography.titleSmall)
                Text("Investigation: ${inv?.title ?: "none (project-level)"}" + (run?.let { " · Run ${shortId(it.id)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { pickInv = true }) { Text("Change") }
            }

            // Voice
            SectionHeader("Voice note")
            val recording = vm.isRecording
            if (recording) {
                var elapsed by rememberSaveable { mutableLongStateOf(0L) }
                LaunchedEffect(Unit) { while (true) { elapsed = (SystemClock.elapsedRealtime() - vm.recordingStartedAt) / 1000; delay(500) } }
                Text("● RECORDING  ${elapsed / 60}:${"%02d".format(elapsed % 60)}", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.stopRecording(save = true) }, modifier = Modifier.heightIn(min = 56.dp)) { Text("■ Stop & save") }
                    OutlinedButton(onClick = { vm.stopRecording(save = false) }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Discard") }
                }
            } else {
                Button(onClick = {
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                        vm.startRecording(RecordingTarget(pid, iid, runId))
                    else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }, modifier = Modifier.heightIn(min = 56.dp)) { Text("● Record voice note") }
                Text("Audio is stored as-is. Transcription is not available in this build.", style = MaterialTheme.typography.labelSmall)
                if (micDenied) Text("Microphone permission was denied. Text, photo and file capture still work. To record, allow the microphone for BLACKBOX in Android Settings → Apps → BLACKBOX → Permissions.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            SectionHeader("Photo or file")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val f = vm.repo.attachments.newCaptureFile(ctx.cacheDir, "jpg")
                    pendingPhoto = f.absolutePath
                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                    try { takePhoto.launch(uri) } catch (e: ActivityNotFoundException) {
                        f.delete(); pendingPhoto = null
                        vm.message = "No camera app is available. Import an existing image instead."
                    }
                }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Take photo") }
                OutlinedButton(onClick = { importFile.launch(arrayOf("*/*")) }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text("Import screenshot / log / file")
                }
            }
            Text("Imported files are copied into BLACKBOX's private storage, so they stay available even if the original is moved or deleted.",
                style = MaterialTheme.typography.labelSmall)

            SectionHeader("Text")
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                listOf(EvidenceKind.TEXT, EvidenceKind.LOG, EvidenceKind.MEASUREMENT).forEach { k ->
                    FilterChip(selected = kind == k, onClick = { kind = k }, modifier = Modifier.padding(end = 6.dp),
                        label = { Text(when (k) { EvidenceKind.LOG -> "Log paste"; EvidenceKind.MEASUREMENT -> "Measurement"; else -> "Note" }) })
                }
            }
            Field("Title (optional)", title, { title = it }, singleLine = true)
            Field(if (kind == EvidenceKind.MEASUREMENT) "Reading, with units (e.g. 12.4 V)" else "What did you see?", text, { text = it }, minLines = 4)
            Button(
                enabled = text.isNotBlank() && !vm.busy,
                onClick = {
                    val t = title; val body = text; val k = kind
                    vm.launch("Save failed") {
                        vm.repo.addTextEvidence(pid, iid, runId, k, t, body)
                        title = ""; text = ""
                        vm.message = "Saved."
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(),
            ) { Text("Save now") }
        }
    }

    if (pickInv && b != null) {
        val options = listOf<Pair<String?, String?>>(null to null) +
            b.investigations.map { it.id to null } +
            b.runs.filter { it.outcome == RunOutcome.NOT_FINISHED }.map { it.investigationId to it.id }
        ChoiceDialog("Attach capture to", options, iid to runId, { (i, r) ->
            when {
                i == null -> "Project only (no investigation)"
                r != null -> "Active run ${shortId(r)} · " + (b.runs.firstOrNull { it.id == r }?.planSnapshot?.title ?: "")
                else -> "Investigation: " + (b.investigations.firstOrNull { it.id == i }?.title ?: i)
            }
        }, { (i, r) -> iid = i; runId = r }, { pickInv = false })
    }
}
