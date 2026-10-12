package fr.bastien.assistant.ui.main

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.bastien.assistant.BuildConfig
import fr.bastien.assistant.data.local.TaskEntity
import fr.bastien.assistant.domain.model.SyncStatus
import fr.bastien.assistant.integration.microsoft.TodoConfig
import fr.bastien.assistant.integration.microsoft.TodoStatus
import kotlinx.coroutines.launch

private enum class Mode { LIST, SEARCH, TRASH }

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onWrite: () -> Unit,
    onConnectTodo: () -> Unit,
    widgetPlaced: () -> Boolean,
    canPinWidget: () -> Boolean,
    onPinWidget: () -> Unit,
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val trash by viewModel.trash.collectAsStateWithLifecycle()
    val todo by viewModel.todoStatus.collectAsStateWithLifecycle()
    val waiting by viewModel.waitingCount.collectAsStateWithLifecycle()
    val errors by viewModel.errorCount.collectAsStateWithLifecycle()
    var showPinButton by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var mode by rememberSaveable { mutableStateOf(Mode.LIST) }
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        showPinButton = canPinWidget() && !widgetPlaced()
        now = System.currentTimeMillis()
        viewModel.syncNow()
    }

    BackHandler(enabled = mode != Mode.LIST) {
        mode = Mode.LIST
        query = ""
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (mode) {
                Mode.LIST -> Column(modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Assistant", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { mode = Mode.SEARCH }) { Icon(Icons.Default.Search, contentDescription = "Rechercher") }
                        IconButton(onClick = { mode = Mode.TRASH }) { Icon(Icons.Default.Delete, contentDescription = "Corbeille") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = onWrite) { Text("✍  Écrire") }
                        if (showPinButton) {
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = onPinWidget) { Text("Ajouter le widget") }
                        }
                    }
                    if (viewModel.todoConfigured) {
                        Spacer(Modifier.height(12.dp))
                        TodoBar(
                            status = todo,
                            waiting = waiting,
                            errors = errors,
                            now = now,
                            connecting = viewModel.connecting,
                            message = viewModel.connectMessage,
                            onConnect = onConnectTodo,
                            onSync = viewModel::syncNow,
                            onDisconnect = viewModel::disconnect,
                        )
                    }
                }

                Mode.SEARCH -> SearchHeader(
                    query = query,
                    onQuery = { query = it },
                    onClose = {
                        mode = Mode.LIST
                        query = ""
                    },
                )

                Mode.TRASH -> TrashHeader(onClose = { mode = Mode.LIST })
            }
            HorizontalDivider()
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (mode == Mode.TRASH) {
                    TrashList(trash, now, onRestore = viewModel::restore)
                } else {
                    val list = tasks
                    val shown = if (mode == Mode.SEARCH && list != null) TaskSearch.filter(list, query) else list
                    when {
                        shown == null -> Unit
                        shown.isEmpty() && mode == Mode.SEARCH -> Message("Aucun résultat.")
                        shown.isEmpty() -> EmptyHistory()
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            items(shown, key = { it.id }) { task ->
                                TaskRow(
                                    task = task,
                                    now = now,
                                    showSync = todo.connected,
                                    onToggle = { done -> viewModel.setDone(task, done) },
                                    onOpen = { editing = task },
                                )
                            }
                        }
                    }
                }
            }
            Text(
                if (todo.connected) {
                    "Version ${BuildConfig.VERSION_NAME} · synchro To Do, liste « ${TodoConfig.LIST_NAME} »"
                } else {
                    "Version ${BuildConfig.VERSION_NAME} · tout reste sur le téléphone"
                },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    editing?.let { task ->
        EditTaskDialog(
            task = task,
            now = now,
            onDismiss = { editing = null },
            onSave = { title ->
                viewModel.rename(task, title)
                editing = null
            },
            onDelete = {
                viewModel.moveToTrash(task)
                editing = null
                scope.launch {
                    val result = snackbar.showSnackbar("Mis à la corbeille", actionLabel = "Annuler", duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) viewModel.restore(task)
                }
            },
        )
    }
}

@Composable
private fun SearchHeader(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }
    Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Fermer la recherche") }
        OutlinedTextField(
            value = query,
            onValueChange = { onQuery(it.replace('\n', ' ')) },
            modifier = Modifier.weight(1f).focusRequester(focus),
            placeholder = { Text("Rechercher") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
    }
}

@Composable
private fun TrashHeader(onClose: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Fermer la corbeille") }
            Text("Corbeille", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "Les tâches supprimées restent ici ${HistoryViewModel.TRASH_DAYS} jours.",
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrashList(trash: List<TaskEntity>, now: Long, onRestore: (TaskEntity) -> Unit) {
    if (trash.isEmpty()) {
        Message("La corbeille est vide.")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(trash, key = { it.id }) { task ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Supprimée ${TimeLabel.format(task.deletedAt ?: task.updatedAt, now).replaceFirstChar { it.lowercase() }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onRestore(task) }) { Text("Restaurer") }
            }
        }
    }
}

@Composable
private fun EditTaskDialog(
    task: TaskEntity,
    now: Long,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var text by remember(task.id) { mutableStateOf(TextFieldValue(task.title, TextRange(task.title.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(task.id) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.copy(text = it.text.replace('\n', ' ')) },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    minLines = 2,
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (text.text.isNotBlank()) onSave(text.text) }),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Noté ${TimeLabel.format(task.createdAt, now).replaceFirstChar { it.lowercase() }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.text) }, enabled = text.text.isNotBlank()) { Text("Enregistrer") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}

@Composable
private fun TodoBar(
    status: TodoStatus,
    waiting: Int,
    errors: Int,
    now: Long,
    connecting: Boolean,
    message: String?,
    onConnect: () -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Column {
        when {
            !status.connected -> OutlinedButton(onClick = onConnect, enabled = !connecting) {
                Text(if (connecting) "Connexion…" else "Connecter To Do")
            }

            status.needsReconnect -> {
                Text("To Do : reconnexion nécessaire", style = MaterialTheme.typography.bodyMedium)
                Row {
                    OutlinedButton(onClick = onConnect, enabled = !connecting) { Text("Reconnecter") }
                    TextButton(onClick = onDisconnect) { Text("Déconnecter") }
                }
            }

            else -> {
                Text(
                    "To Do · ${status.accountName ?: "connecté"}",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    buildString {
                        append("Liste « ${TodoConfig.LIST_NAME} »")
                        status.lastSyncAt?.let { append(" · synchro ${TimeLabel.format(it, now)}") }
                        if (waiting > 0) append(" · $waiting à envoyer")
                        if (errors > 0) append(" · $errors en erreur")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    TextButton(onClick = onSync) { Text("Synchroniser") }
                    TextButton(onClick = onDisconnect) { Text("Déconnecter") }
                }
            }
        }
        val problem = message ?: status.lastError?.takeIf { status.connected }
        if (problem != null) {
            Text(problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun TaskRow(
    task: TaskEntity,
    now: Long,
    showSync: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
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
            val syncLabel = when {
                !showSync -> null
                task.syncStatus == SyncStatus.WAITING -> " · à envoyer"
                task.syncStatus == SyncStatus.ERROR -> " · erreur d'envoi"
                else -> null
            }
            Text(
                TimeLabel.format(task.createdAt, now) + syncLabel.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = if (task.syncStatus == SyncStatus.ERROR && showSync) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        modifier = Modifier.padding(24.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
