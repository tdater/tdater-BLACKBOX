package com.tdater.blackbox.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.tdater.blackbox.core.model.EvidenceKind
import com.tdater.blackbox.ui.MainViewModel
import com.tdater.blackbox.ui.components.BbTopBar
import com.tdater.blackbox.ui.components.ConfirmDialog
import com.tdater.blackbox.ui.components.Field
import com.tdater.blackbox.ui.components.LabeledValue
import com.tdater.blackbox.ui.components.SectionHeader
import com.tdater.blackbox.ui.components.Tag
import com.tdater.blackbox.ui.components.Tone
import com.tdater.blackbox.ui.components.fmtFull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_PREVIEW_PX = 2048

/** Decodes an image no larger than [MAX_PREVIEW_PX] on its long edge to bound memory use. */
private fun decodeBounded(file: File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_PREVIEW_PX) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}

@Composable
fun EvidenceScreen(vm: MainViewModel, pid: String, eid: String, back: () -> Unit, openHit: (String, String?, String?) -> Unit = { _, _, _ -> }) {
    val bundle by rememberBundle(vm, pid)
    val ctx = LocalContext.current
    val b = bundle
    val e = b?.evidence?.firstOrNull { it.id == eid }
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmWithdraw by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable(eid) { mutableStateOf<String?>(null) }
    var summary by rememberSaveable(eid) { mutableStateOf<String?>(null) }
    var labels by rememberSaveable(eid) { mutableStateOf<String?>(null) }

    Scaffold(topBar = { BbTopBar(e?.title ?: "Evidence", e?.kind?.name, onBack = back) }) { pad ->
        if (b == null) return@Scaffold
        if (e == null) { Column(Modifier.padding(pad)) { Missing("Evidence") }; return@Scaffold }
        val file = e.attachmentPath?.takeIf { vm.repo.attachments.exists(it) }?.let { vm.repo.attachments.file(it) }
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp).widthIn(max = 900.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(e.kind.name, Tone.INFO)
                Tag(e.source.name)
                if (e.withdrawnAt != null) Tag("WITHDRAWN ${fmtFull(e.withdrawnAt)}", Tone.BAD)
            }
            if (e.attachmentPath != null && file == null) {
                Text(if (e.withdrawnAt != null) "The attachment file was removed when this evidence was withdrawn."
                     else "The attachment file is missing from app storage. The record and its checksum are kept.",
                    color = MaterialTheme.colorScheme.error)
            }
            if (file != null && (e.kind == EvidenceKind.PHOTO || e.kind == EvidenceKind.IMAGE_IMPORT)) {
                var bmp by remember(file.path) { mutableStateOf<Bitmap?>(null) }
                var failed by remember(file.path) { mutableStateOf(false) }
                LaunchedEffect(file.path) {
                    val r = withContext(Dispatchers.IO) { runCatching { decodeBounded(file) }.getOrNull() }
                    if (r == null) failed = true else bmp = r
                }
                bmp?.let { Image(it.asImageBitmap(), contentDescription = e.title, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) }
                if (failed) Text("Preview unavailable for this image format. Use Open with…", color = MaterialTheme.colorScheme.error)
            }
            if (file != null && e.kind == EvidenceKind.AUDIO) AudioPlayer(file)
            if (e.text.isNotBlank()) {
                SectionHeader("Content")
                SelectionContainer {
                    Text(e.text, fontFamily = if (e.kind == EvidenceKind.LOG) FontFamily.Monospace else null,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (file != null) OutlinedButton(onClick = {
                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
                val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, e.mimeType ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try { ctx.startActivity(Intent.createChooser(view, "Open with")) } catch (_: ActivityNotFoundException) { vm.message = "No app can open this file type." }
            }) { Text("Open with…") }

            SectionHeader("Details")
            LabeledValue("Summary", e.userSummary.ifBlank { "—" })
            LabeledValue("Labels", e.labels.joinToString().ifBlank { "—" })
            LabeledValue("Captured / recorded", fmtFull(e.capturedAt))
            e.importedAt?.let { LabeledValue("Imported", fmtFull(it)) }
            LabeledValue("Original timestamp", e.originalTimestamp?.let(::fmtFull) ?: "not available")
            e.originalFilename?.let { LabeledValue("Original filename", it) }
            e.mimeType?.let { LabeledValue("Type", it) }
            e.attachmentBytes?.let { LabeledValue("Size", "$it bytes") }
            e.attachmentSha256?.let { LabeledValue("SHA-256", it, mono = true) }
            LabeledValue("Record id", e.id, mono = true)
            val citing = b.claims.filter { eid in it.evidenceIds }
            if (citing.isNotEmpty()) LabeledValue("Cited by", "${citing.size} claim(s)")

            if (e.withdrawnAt == null && (e.title.length + e.text.length) > 8) {
                SimilarSection(vm, "${e.title}\n${e.text.take(4_000)}\n${e.userSummary}", excludeInvestigationId = e.investigationId,
                    excludeIds = setOf(e.id)) { h ->
                    openHit(h.projectId, h.investigationId, if (h.kind == com.tdater.blackbox.core.search.HitKind.EVIDENCE) h.id else null)
                }
            }

            if (e.withdrawnAt == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { editing = true; title = e.title; summary = e.userSummary; labels = e.labels.joinToString(", ") }) { Text("Edit details") }
                TextButton(onClick = { confirmWithdraw = true }) { Text("Withdraw…", color = MaterialTheme.colorScheme.error) }
            }
            if (editing) {
                Field("Title", title ?: "", { title = it }, singleLine = true)
                Field("Summary / interpretation", summary ?: "", { summary = it }, minLines = 2)
                Field("Labels (comma separated)", labels ?: "", { labels = it }, singleLine = true)
                Button(onClick = {
                    val t = title.orEmpty().ifBlank { e.title }; val s = summary.orEmpty()
                    val l = labels.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    editing = false
                    vm.launch { vm.repo.updateEvidenceText(e.id, t, s, l) }
                }) { Text("Save") }
            }
        }
        if (confirmWithdraw) {
            val citing = b.claims.count { eid in it.evidenceIds }
            ConfirmDialog(
                "Withdraw this evidence?",
                "The stored file will be deleted from this device. The record stays in history marked WITHDRAWN so nothing silently points at missing proof." +
                    if (citing > 0) " $citing claim(s) cite it and will be flagged as unsupported." else "",
                "Withdraw", destructive = true,
                onConfirm = { vm.launch { vm.repo.withdrawEvidence(e.id) } },
                onDismiss = { confirmWithdraw = false },
            )
        }
    }
}

@Composable
private fun AudioPlayer(file: File) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(file.path) { onDispose { player?.release(); player = null } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (player == null) Button(onClick = {
            error = null
            player = try {
                MediaPlayer().apply {
                    setDataSource(file.path); prepare(); start()
                    setOnCompletionListener { it.release(); player = null }
                }
            } catch (t: Exception) { error = "Playback failed: ${t.message}"; null }
        }) { Text("▶ Play") }
        else OutlinedButton(onClick = { player?.release(); player = null }) { Text("■ Stop") }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Text("Audio only. No transcript exists for this recording.", style = MaterialTheme.typography.labelSmall)
}
