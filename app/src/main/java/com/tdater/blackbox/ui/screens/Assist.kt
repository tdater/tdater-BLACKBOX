package com.tdater.blackbox.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tdater.blackbox.core.model.Investigation
import com.tdater.blackbox.core.search.SimilarHit
import com.tdater.blackbox.core.search.SimilarRecords
import com.tdater.blackbox.system.Reminders
import com.tdater.blackbox.ui.MainViewModel
import com.tdater.blackbox.ui.components.Field
import com.tdater.blackbox.ui.components.InfoCard
import com.tdater.blackbox.ui.components.SectionHeader
import com.tdater.blackbox.ui.components.Tag
import com.tdater.blackbox.ui.components.Tone
import com.tdater.blackbox.ui.components.fmtTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * "Seen something like this before?" Looks through every other project and problem on the phone for records that
 * share distinctive words (error codes, part names) with [query], and shows which words matched.
 */
@Composable
fun SimilarSection(vm: MainViewModel, query: String, excludeInvestigationId: String? = null, excludeIds: Set<String> = emptySet(),
                   title: String = "Seen this before?", onOpen: (SimilarHit) -> Unit) {
    var hits by remember(query) { mutableStateOf<List<SimilarHit>?>(null) }
    LaunchedEffect(query) {
        hits = withContext(Dispatchers.Default) {
            runCatching { SimilarRecords.find(query, vm.repo.loadAllBundles(), excludeInvestigationId, excludeIds, limit = 5) }.getOrDefault(emptyList())
        }
    }
    val list = hits ?: return
    SectionHeader(title)
    if (list.isEmpty()) {
        Text("Nothing similar in your other records yet.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Text("Matched on shared words only. Worth a look, not proof.", style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    list.forEach { h ->
        InfoCard(Modifier.padding(vertical = 3.dp).clickable { onOpen(h) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(h.kind.name, Tone.INFO)
                Tag(h.projectTitle.uppercase().take(24))
            }
            Text(h.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(h.snippet, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text("Shared: " + h.sharedTerms.joinToString(", ") + " · ${fmtTime(h.timestamp)}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Pick when to be reminded about a problem. The reminder also shows on the home screen, even with notifications off. */
@Composable
fun FollowUpDialog(inv: Investigation, onDismiss: () -> Unit, onSet: (Long?, String) -> Unit) {
    val ctx = LocalContext.current
    val zone = ZoneId.systemDefault()
    fun at(days: Long) = LocalDate.now(zone).plusDays(days).atTime(LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli()
    val options = listOf("Tomorrow morning" to at(1), "In 3 days" to at(3), "Next week" to at(7), "In 2 weeks" to at(14), "In a month" to at(30))
    var picked by rememberSaveable { mutableStateOf(1) }
    var note by rememberSaveable { mutableStateOf(inv.followUpNote) }
    var pending by remember { mutableStateOf<Long?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        pending?.let { onSet(it, note) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remind me about this") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEachIndexed { i, (label, ms) ->
                    FilterChip(selected = picked == i, onClick = { picked = i }, label = { Text("$label · ${fmtTime(ms)}") })
                }
                Field("What to check (optional)", note, { note = it }, placeholder = "Battery voltage after sitting two days")
                Text("Shows as a notification and on the home screen. It stays on this phone.", style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ms = options[picked].second
                if (Build.VERSION.SDK_INT >= 33 && !Reminders(ctx).canNotify()) { pending = ms; ask.launch(Manifest.permission.POST_NOTIFICATIONS) }
                else onSet(ms, note)
            }) { Text("Set reminder") }
        },
        dismissButton = {
            Row {
                if (inv.followUpAt != null) TextButton(onClick = { onSet(null, "") }) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
