package fr.bastien.assistant.ui.main

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.bastien.assistant.AssistantApp
import fr.bastien.assistant.data.local.TaskEntity
import fr.bastien.assistant.domain.model.SyncStatus
import fr.bastien.assistant.integration.microsoft.ConnectResult
import fr.bastien.assistant.integration.microsoft.GraphTodoApi
import fr.bastien.assistant.integration.microsoft.TodoConfig
import fr.bastien.assistant.integration.microsoft.TodoStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val app: AssistantApp) : ViewModel() {

    private val dao = app.database.dao()

    /** null tant que la base n'a pas répondu, pour ne pas afficher « vide » à tort. */
    val tasks: StateFlow<List<TaskEntity>?> = dao.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val todoConfigured: Boolean = TodoConfig.isConfigured
    val todoStatus: StateFlow<TodoStatus> = app.todoState.status
    val waitingCount: StateFlow<Int> = dao.observeCount(SyncStatus.WAITING)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val errorCount: StateFlow<Int> = dao.observeCount(SyncStatus.ERROR)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Corbeille : ce qui a été supprimé ces 30 derniers jours. */
    val trash: StateFlow<List<TaskEntity>> = dao.observeTrash(System.currentTimeMillis() - TRASH_DAYS * DAY_MS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var connecting by mutableStateOf(false)
        private set
    var connectMessage by mutableStateOf<String?>(null)
        private set

    /** Historique déverrouillé (empreinte) : gardé ici pour ne pas redemander en pliant/dépliant. */
    var unlocked by mutableStateOf(false)
    var stoppedAt: Long = 0L

    fun setDone(task: TaskEntity, done: Boolean) {
        app.appScope.launch { app.taskService.setDone(task.id, done) }
    }

    fun rename(task: TaskEntity, title: String) {
        app.appScope.launch { app.taskService.rename(task.id, title) }
    }

    fun moveToTrash(task: TaskEntity) {
        app.appScope.launch { app.taskService.moveToTrash(task.id) }
    }

    fun restore(task: TaskEntity) {
        app.appScope.launch { app.taskService.restore(task.id) }
    }

    fun authorizationIntent(): Intent {
        connectMessage = null
        connecting = true
        return app.microsoftAuth.authorizationIntent()
    }

    fun onAuthorizationResult(data: Intent?) {
        viewModelScope.launch {
            try {
                when (val result = app.microsoftAuth.completeAuthorization(data)) {
                    is ConnectResult.Ok -> {
                        val name = runCatching { GraphTodoApi(app.httpClient, result.accessToken).accountName() }.getOrNull()
                        app.todoState.markConnected(name)
                        // Les captures déjà faites partent aussi dans la liste de test.
                        dao.queueUnsentTasks()
                        app.syncScheduler.start()
                    }
                    is ConnectResult.Failed -> connectMessage = "Connexion impossible : ${result.message}"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                connectMessage = "Connexion impossible (${e.javaClass.simpleName})"
            } finally {
                connecting = false
            }
        }
    }

    fun onAuthorizationUnavailable() {
        connecting = false
        connectMessage = "Aucun navigateur pour afficher la connexion Microsoft."
    }

    /** Synchro à la demande (et à chaque ouverture de l'appli) ; les envois en erreur sont retentés. */
    fun syncNow() {
        if (!app.todoState.connected) return
        app.appScope.launch {
            dao.retryErrors()
            app.syncScheduler.requestSync()
        }
    }

    fun disconnect() {
        app.syncScheduler.stop()
        app.microsoftAuth.disconnect()
        app.todoState.markDisconnected()
    }

    companion object {
        const val TRASH_DAYS = 30L
        private const val DAY_MS = 24L * 60 * 60 * 1000

        val Factory = viewModelFactory {
            initializer { HistoryViewModel(this[APPLICATION_KEY] as AssistantApp) }
        }
    }
}
