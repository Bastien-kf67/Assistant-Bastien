package fr.bastien.assistant.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.bastien.assistant.AssistantApp
import fr.bastien.assistant.data.TaskService
import fr.bastien.assistant.data.local.AssistantDao
import fr.bastien.assistant.data.local.TaskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(
    dao: AssistantDao,
    private val taskService: TaskService,
    private val appScope: CoroutineScope,
) : ViewModel() {

    /** null tant que la base n'a pas répondu, pour ne pas afficher « vide » à tort. */
    val tasks: StateFlow<List<TaskEntity>?> = dao.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setDone(task: TaskEntity, done: Boolean) {
        appScope.launch { taskService.setDone(task.id, done) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AssistantApp
                HistoryViewModel(app.database.dao(), app.taskService, app.appScope)
            }
        }
    }
}
