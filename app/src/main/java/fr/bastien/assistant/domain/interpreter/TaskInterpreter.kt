package fr.bastien.assistant.domain.interpreter

import fr.bastien.assistant.domain.model.Category
import fr.bastien.assistant.domain.model.TaskType
import java.time.ZonedDateTime

/** Une tâche (ou idée) déduite d'une capture par l'IA. */
data class InterpretedTask(
    val title: String,
    val type: TaskType,
    val category: Category,
    /** Date ISO aaaa-mm-jj, seulement si la capture en donne une. */
    val dueDate: String?,
    val confidence: Double,
    val needsClarification: Boolean,
    val clarificationQuestion: String?,
)

/**
 * Transforme un texte libre en tâches. Générique : on peut changer de fournisseur d'IA
 * sans toucher au reste de l'appli.
 */
interface TaskInterpreter {
    suspend fun interpret(text: String, now: ZonedDateTime): List<InterpretedTask>
}

/** Échec de l'IA. [status] = 0 quand le réseau a échoué. */
class InterpreterException(val status: Int, message: String) : Exception(message) {
    /** Panne passagère (réseau, surcharge) : on réessaiera. */
    val isTransient: Boolean
        get() = status == 0 || status == 408 || status == 429 || status >= 500
}
