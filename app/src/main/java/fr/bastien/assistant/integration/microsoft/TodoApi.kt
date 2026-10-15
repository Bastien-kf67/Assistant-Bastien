package fr.bastien.assistant.integration.microsoft

/** Ce que la synchro attend de To Do. Une fausse version sert dans les tests. */
interface TodoApi {
    /** Identifiant de la liste portant ce nom, créée si elle n'existe pas. */
    suspend fun findOrCreateList(name: String): String

    /** Crée une tâche et renvoie son identifiant To Do. */
    suspend fun createTask(listId: String, task: TaskPayload): String

    /** Met à jour titre, état « fait », échéance et case. */
    suspend fun updateTask(listId: String, taskId: String, task: TaskPayload)

    /** Supprime une tâche ; déjà absente = pas une erreur. */
    suspend fun deleteTask(listId: String, taskId: String)

    /** Changements depuis [deltaLink] (tout le contenu de la liste si null). */
    suspend fun delta(listId: String, deltaLink: String?): DeltaResult
}

/** Ce qu'Assistant envoie à To Do pour une tâche. */
data class TaskPayload(
    val title: String,
    val completed: Boolean,
    /** aaaa-mm-jj, ou null : pas d'échéance. */
    val dueDate: String? = null,
    /** Case de rangement, affichée comme étiquette dans To Do. */
    val categories: List<String> = emptyList(),
    /** Texte d'origine, mis en note quand l'IA a reformulé. */
    val note: String? = null,
)

data class RemoteTask(
    val id: String,
    val title: String,
    val completed: Boolean,
    val modifiedAt: Long?,
    val removed: Boolean = false,
)

data class DeltaResult(val items: List<RemoteTask>, val deltaLink: String?)

/** Réponse en erreur de Microsoft. [status] = 0 quand le réseau a échoué. */
class TodoApiException(val status: Int, message: String) : Exception(message) {
    /** Panne passagère : on réessaiera plus tard sans rien changer. */
    val isTransient: Boolean
        get() = status == 0 || status == 408 || status == 429 || status >= 500

    val isAuth: Boolean
        get() = status == 401 || status == 403
}
