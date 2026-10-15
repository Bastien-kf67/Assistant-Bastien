package fr.bastien.assistant.integration.microsoft

/**
 * Faux To Do en mémoire, qui se comporte comme Microsoft Graph sur les points utiles :
 * le premier delta renvoie toute la liste, les suivants seulement les changements (y compris les nôtres).
 */
class FakeTodoApi : TodoApi {

    data class FakeTask(
        val id: String,
        val title: String,
        val completed: Boolean,
        val dueDate: String? = null,
        val categories: List<String> = emptyList(),
        val note: String? = null,
    )

    val lists = mutableMapOf<String, String>()
    val tasks = linkedMapOf<String, FakeTask>()
    private val changes = mutableListOf<RemoteTask>()
    private var nextId = 1
    private var deltaCount = 0

    var createCalls = 0
        private set
    var updateCalls = 0
        private set
    var listCreations = 0
        private set

    /** Erreurs à renvoyer au prochain appel concerné. */
    var failNextCreate: TodoApiException? = null
    var failNextUpdate: TodoApiException? = null
    var failNextDeltaWithLink: TodoApiException? = null

    override suspend fun findOrCreateList(name: String): String {
        lists.entries.firstOrNull { it.value == name }?.let { return it.key }
        listCreations++
        val id = "list-${nextId++}"
        lists[id] = name
        return id
    }

    override suspend fun createTask(listId: String, task: TaskPayload): String {
        failNextCreate?.let { failNextCreate = null; throw it }
        if (listId !in lists) throw TodoApiException(404, "list not found")
        createCalls++
        val id = addRemote(task.title, task.completed)
        tasks[id] = tasks.getValue(id).copy(dueDate = task.dueDate, categories = task.categories, note = task.note)
        return id
    }

    override suspend fun updateTask(listId: String, taskId: String, task: TaskPayload) {
        failNextUpdate?.let { failNextUpdate = null; throw it }
        val existing = tasks[taskId] ?: throw TodoApiException(404, "task not found")
        updateCalls++
        // Comme GraphTodoApi : la note n'est jamais réécrite en mise à jour.
        tasks[taskId] = existing.copy(
            title = task.title,
            completed = task.completed,
            dueDate = task.dueDate,
            categories = task.categories,
        )
        changes += remote(tasks.getValue(taskId))
    }

    var deleteCalls = 0
        private set

    override suspend fun deleteTask(listId: String, taskId: String) {
        deleteCalls++
        // Comme Graph côté appli : une tâche déjà absente n'est pas une erreur.
        if (tasks.remove(taskId) != null) {
            changes += RemoteTask(taskId, "", completed = false, modifiedAt = null, removed = true)
        }
    }

    override suspend fun delta(listId: String, deltaLink: String?): DeltaResult {
        if (listId !in lists) throw TodoApiException(404, "list not found")
        if (deltaLink != null) failNextDeltaWithLink?.let { failNextDeltaWithLink = null; throw it }
        val items = if (deltaLink == null) tasks.values.map(::remote) else changes.toList()
        changes.clear()
        return DeltaResult(items, "delta-${++deltaCount}")
    }

    // --- Actions faites « dans To Do » par l'utilisateur ---

    fun addRemote(title: String, completed: Boolean = false): String {
        val id = "remote-${nextId++}"
        tasks[id] = FakeTask(id, title, completed)
        changes += remote(tasks.getValue(id))
        return id
    }

    fun completeRemote(id: String) {
        tasks[id] = tasks.getValue(id).copy(completed = true)
        changes += remote(tasks.getValue(id))
    }

    fun renameRemote(id: String, title: String) {
        tasks[id] = tasks.getValue(id).copy(title = title)
        changes += remote(tasks.getValue(id))
    }

    fun deleteRemote(id: String) {
        tasks.remove(id)
        changes += RemoteTask(id, "", completed = false, modifiedAt = null, removed = true)
    }

    fun deleteList(id: String) {
        lists.remove(id)
        tasks.clear()
        changes.clear()
    }

    private fun remote(task: FakeTask) = RemoteTask(task.id, task.title, task.completed, modifiedAt = 1_760_000_000_000L)
}
