package com.tdater.blackbox.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import com.tdater.blackbox.ui.theme.BbColors
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
    Column {
        TopAppBar(
            title = {
                Column {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                    if (subtitle != null) Text(subtitle.uppercase(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                }
            },
            navigationIcon = {
                if (onBack != null) IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                }
            },
            actions = { actions() },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface),
        )
        Box(Modifier.fillMaxWidth().height(2.dp).background(SignalBrush))
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 16.dp).background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall))
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(
            listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), Color.Transparent))))
        trailing()
    }
}

/** Status tag with a text label (never colour alone). */
@Composable
fun Tag(text: String, tone: Tone = Tone.NEUTRAL) {
    val fg = toneColor(tone)
    Surface(color = fg.copy(alpha = 0.13f), shape = CircleShape, border = BorderStroke(1.dp, fg.copy(alpha = 0.45f))) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
            Box(Modifier.size(6.dp).background(fg, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(text, color = fg, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

enum class Tone { GOOD, BAD, WARN, INFO, NEUTRAL }

@Composable
fun toneColor(tone: Tone): Color = when (tone) {
    Tone.GOOD -> BbColors.Good
    Tone.BAD -> BbColors.Bad
    Tone.WARN -> BbColors.Warn
    Tone.INFO -> BbColors.Cyan
    Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
}

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
            TraceGlyph(size = 96.dp)
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            action()
        }
    }
}

/** Glassy instrument panel. [accent] paints a glowing left edge (e.g. a run's outcome colour). */
@Composable
fun InfoCard(modifier: Modifier = Modifier, accent: Color? = null, content: @Composable () -> Unit) {
    val edge = accent ?: MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(edge.copy(alpha = 0.55f), Color.White.copy(alpha = 0.06f),
            BbColors.Cyan.copy(alpha = 0.18f)))),
    ) {
        Column(
            Modifier.drawBehind {
                if (accent != null) drawRect(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.15f))),
                    size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height))
            }.padding(start = if (accent != null) 18.dp else 14.dp, top = 14.dp, end = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) { content() }
    }
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
