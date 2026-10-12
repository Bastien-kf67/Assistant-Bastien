package fr.bastien.assistant.integration.microsoft

/**
 * Faux To Do en mémoire, qui se comporte comme Microsoft Graph sur les points utiles :
 * le premier delta renvoie toute la liste, les suivants seulement les changements (y compris les nôtres).
 */
class FakeTodoApi : TodoApi {

    data class FakeTask(val id: String, val title: String, val completed: Boolean)

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

    override suspend fun createTask(listId: String, title: String, completed: Boolean): String {
        failNextCreate?.let { failNextCreate = null; throw it }
        if (listId !in lists) throw TodoApiException(404, "list not found")
        createCalls++
        return addRemote(title, completed)
    }

    override suspend fun updateStatus(listId: String, taskId: String, completed: Boolean) {
        failNextUpdate?.let { failNextUpdate = null; throw it }
        val task = tasks[taskId] ?: throw TodoApiException(404, "task not found")
        updateCalls++
        tasks[taskId] = task.copy(completed = completed)
        changes += remote(tasks.getValue(taskId))
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
