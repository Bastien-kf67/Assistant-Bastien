package fr.bastien.assistant.integration.microsoft

/** Ce que la synchro attend de To Do. Une fausse version sert dans les tests. */
interface TodoApi {
    /** Identifiant de la liste portant ce nom, créée si elle n'existe pas. */
    suspend fun findOrCreateList(name: String): String

    /** Crée une tâche et renvoie son identifiant To Do. */
    suspend fun createTask(listId: String, title: String, completed: Boolean): String

    suspend fun updateStatus(listId: String, taskId: String, completed: Boolean)

    /** Changements depuis [deltaLink] (tout le contenu de la liste si null). */
    suspend fun delta(listId: String, deltaLink: String?): DeltaResult
}

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
