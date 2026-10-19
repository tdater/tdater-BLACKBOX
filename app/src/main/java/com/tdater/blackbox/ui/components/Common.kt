package com.tdater.blackbox.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tdater.blackbox.core.compare.DiffStatus
import com.tdater.blackbox.core.model.ClaimClassification
import com.tdater.blackbox.core.model.RunOutcome
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFmt = DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault())
private val fullFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault())

fun fmtTime(ms: Long?): String = ms?.let { timeFmt.format(Instant.ofEpochMilli(it)) } ?: "—"
fun fmtFull(ms: Long?): String = ms?.let { fullFmt.format(Instant.ofEpochMilli(it)) } ?: "unknown"
fun shortId(id: String) = id.take(8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BbTopBar(title: String, subtitle: String? = null, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        },
        actions = { actions() },
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Status tag with a text label (never colour alone). */
@Composable
fun Tag(text: String, tone: Tone = Tone.NEUTRAL) {
    val (bg, fg) = when (tone) {
        Tone.GOOD -> Color(0xFF173D2A) to Color(0xFFA6E8BF)
        Tone.BAD -> Color(0xFF4A1A17) to Color(0xFFFFB4AB)
        Tone.WARN -> Color(0xFF443300) to Color(0xFFFFD98A)
        Tone.INFO -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        Tone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = bg, shape = MaterialTheme.shapes.small) {
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

enum class Tone { GOOD, BAD, WARN, INFO, NEUTRAL }

fun outcomeTone(o: RunOutcome) = when (o) {
    RunOutcome.PASS -> Tone.GOOD
    RunOutcome.FAIL -> Tone.BAD
    RunOutcome.PARTIAL, RunOutcome.INCONCLUSIVE -> Tone.WARN
    RunOutcome.NOT_FINISHED -> Tone.INFO
    RunOutcome.ABANDONED -> Tone.NEUTRAL
}

fun outcomeLabel(o: RunOutcome) = when (o) {
    RunOutcome.PASS -> "✓ PASS"
    RunOutcome.FAIL -> "✗ FAIL"
    RunOutcome.PARTIAL -> "◐ PARTIAL"
    RunOutcome.INCONCLUSIVE -> "? INCONCLUSIVE"
    RunOutcome.NOT_FINISHED -> "● IN PROGRESS"
    RunOutcome.ABANDONED -> "– ABANDONED"
}

fun claimLabel(c: ClaimClassification) = when (c) {
    ClaimClassification.OBSERVED -> "OBSERVED"
    ClaimClassification.INFERRED -> "INFERRED (hypothesis)"
    ClaimClassification.SUPPORTED_RESULT -> "SUPPORTED BY TEST"
    ClaimClassification.UNKNOWN -> "UNKNOWN"
}

fun claimTone(c: ClaimClassification) = when (c) {
    ClaimClassification.OBSERVED -> Tone.INFO
    ClaimClassification.INFERRED -> Tone.WARN
    ClaimClassification.SUPPORTED_RESULT -> Tone.GOOD
    ClaimClassification.UNKNOWN -> Tone.NEUTRAL
}

fun diffLabel(s: DiffStatus) = when (s) {
    DiffStatus.CHANGED -> "≠ CHANGED"
    DiffStatus.UNCHANGED_KNOWN -> "= SAME"
    DiffStatus.UNKNOWN -> "? UNKNOWN"
    DiffStatus.MISSING -> "∅ MISSING"
    DiffStatus.NOT_COMPARABLE -> "⊘ NOT COMPARABLE"
}

fun diffTone(s: DiffStatus) = when (s) {
    DiffStatus.CHANGED -> Tone.WARN
    DiffStatus.UNCHANGED_KNOWN -> Tone.NEUTRAL
    DiffStatus.UNKNOWN, DiffStatus.MISSING -> Tone.INFO
    DiffStatus.NOT_COMPARABLE -> Tone.BAD
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.widthIn(max = 420.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            action()
        }
    }
}

@Composable
fun InfoCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() } }
}

@Composable
fun LabeledValue(label: String, value: String, mono: Boolean = false) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = if (mono) FontFamily.Monospace else null)
    }
}

@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
          singleLine: Boolean = false, minLines: Int = 1, placeholder: String? = null) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = singleLine,
        minLines = minLines, placeholder = placeholder?.let { { Text(it) } }, modifier = modifier.fillMaxWidth())
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = false) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
