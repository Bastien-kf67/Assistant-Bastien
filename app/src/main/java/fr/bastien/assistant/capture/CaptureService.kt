package fr.bastien.assistant.capture

import androidx.room.withTransaction
import fr.bastien.assistant.data.local.ActionLogEntity
import fr.bastien.assistant.data.local.AssistantDatabase
import fr.bastien.assistant.data.local.CaptureEntity
import fr.bastien.assistant.data.local.TaskEntity
import fr.bastien.assistant.domain.model.CaptureStatus
import fr.bastien.assistant.domain.model.TaskType
import java.util.UUID

sealed interface CaptureResult {
    /** Capture enregistrée. [captureId] peut différer de l'identifiant demandé (voir [CaptureService.capture]). */
    data class Saved(val captureId: String) : CaptureResult

    /** Même demande reçue deux fois (double appui) : rien de nouveau n'a été créé. */
    data class AlreadySaved(val captureId: String) : CaptureResult

    /** Texte vide : rien à enregistrer. */
    data object Empty : CaptureResult
}

/**
 * Le seul moteur de capture. Texte, voix et toutes les futures entrées passent ici.
 *
 * Il enregistre d'abord, en local, avant tout traitement. Aucun appel réseau ici.
 */
class CaptureService(
    private val db: AssistantDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao = db.dao()

    /**
     * Enregistre [rawText] tel quel.
     *
     * [requestId] est fixé à l'ouverture de l'écran de saisie : deux envois avec le même identifiant
     * et le même texte ne créent qu'une capture. Si l'identifiant existe déjà avec un autre texte
     * (identifiant périmé), la capture est enregistrée sous un nouvel identifiant : rien n'est perdu.
     */
    suspend fun capture(requestId: String, rawText: String, source: CaptureSource): CaptureResult {
        if (rawText.isBlank()) return CaptureResult.Empty
        return db.withTransaction {
            val existing = dao.getCapture(requestId)
            when {
                existing == null -> {
                    insertNew(requestId, rawText, source)
                    CaptureResult.Saved(requestId)
                }
                existing.originalText == rawText -> CaptureResult.AlreadySaved(requestId)
                else -> {
                    val freshId = newId()
                    insertNew(freshId, rawText, source)
                    CaptureResult.Saved(freshId)
                }
            }
        }
    }

    suspend fun exists(captureId: String): Boolean = dao.captureExists(captureId)

    private suspend fun insertNew(id: String, rawText: String, source: CaptureSource) {
        val now = clock()
        dao.insertCapture(
            CaptureEntity(
                id = id,
                userId = LOCAL_USER_ID,
                originalText = rawText,
                source = source,
                createdAt = now,
                updatedAt = now,
                status = CaptureStatus.CAPTURED,
            ),
        )
        // Jalon 1 : sans IA, une capture donne une seule tâche dont le titre est le texte saisi.
        dao.insertTask(
            TaskEntity(
                id = newId(),
                captureId = id,
                type = TaskType.TASK,
                title = titleFrom(rawText),
                createdAt = now,
                updatedAt = now,
            ),
        )
        dao.insertLog(
            ActionLogEntity(
                captureId = id,
                timestamp = now,
                action = "CAPTURE_RECEIVED",
                detail = "source=${source.name} texte=\"$rawText\"",
            ),
        )
    }

    companion object {
        /** Utilisateur unique pour l'instant ; le champ existe pour pouvoir ouvrir l'outil à d'autres. */
        const val LOCAL_USER_ID = "local-user"

        fun titleFrom(rawText: String): String = rawText.trim().replace(Regex("\\s+"), " ")
    }
}
