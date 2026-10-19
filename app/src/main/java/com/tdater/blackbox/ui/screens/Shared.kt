package com.tdater.blackbox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowWidthSizeClass
import com.tdater.blackbox.core.model.ProjectBundle
import com.tdater.blackbox.ui.MainViewModel

/** Live view of every record in one project. Null until loaded or if the project was deleted. */
@Composable
fun rememberBundle(vm: MainViewModel, pid: String): State<ProjectBundle?> =
    remember(pid) { vm.repo.observeBundle(pid) }.collectAsStateWithLifecycle(initialValue = null)

/** Layout facts derived from the real current window, not a hard-coded device size. */
data class LayoutInfo(val wide: Boolean, val expanded: Boolean, val tabletop: Boolean)

@Composable
fun layoutInfo(): LayoutInfo {
    val info = currentWindowAdaptiveInfo()
    val width = info.windowSizeClass.windowWidthSizeClass
    return LayoutInfo(
        wide = width != WindowWidthSizeClass.COMPACT,
        expanded = width == WindowWidthSizeClass.EXPANDED,
        tabletop = info.windowPosture.isTabletop,
    )
}

@Composable
fun <T> ChoiceDialog(title: String, options: List<T>, selected: T?, label: (T) -> String, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                options.forEach { o ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(o); onDismiss() }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = o == selected, onClick = { onPick(o); onDismiss() })
                        Text(label(o), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun Missing(what: String) {
    com.tdater.blackbox.ui.components.EmptyState("$what not found", "It may have been deleted. Go back and pick another record.")
}
