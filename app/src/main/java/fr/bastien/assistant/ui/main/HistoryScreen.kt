package fr.bastien.assistant.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.bastien.assistant.BuildConfig
import fr.bastien.assistant.data.local.TaskEntity

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onWrite: () -> Unit,
    widgetPlaced: () -> Boolean,
    canPinWidget: () -> Boolean,
    onPinWidget: () -> Unit,
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    var showPinButton by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        showPinButton = canPinWidget() && !widgetPlaced()
        now = System.currentTimeMillis()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text("Assistant", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onWrite) { Text("✍  Écrire") }
                    if (showPinButton) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = onPinWidget) { Text("Ajouter le widget") }
                    }
                }
            }
            HorizontalDivider()
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val list = tasks
                when {
                    list == null -> Unit
                    list.isEmpty() -> EmptyHistory()
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(list, key = { it.id }) { task ->
                            TaskRow(task, now, onToggle = { done -> viewModel.setDone(task, done) })
                        }
                    }
                }
            }
            Text(
                "Version ${BuildConfig.VERSION_NAME} · tout reste sur le téléphone",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TaskRow(task: TaskEntity, now: Long, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!task.done) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = task.done, onCheckedChange = onToggle)
        Column(modifier = Modifier.padding(end = 12.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                color = if (task.done) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                TimeLabel.format(task.createdAt, now),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyHistory() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Aucune capture pour l'instant.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Touche « Écrire » sur le widget dès qu'une chose à faire te passe par la tête.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
