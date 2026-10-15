package fr.bastien.assistant.domain.interpreter

import androidx.room.withTransaction
import fr.bastien.assistant.capture.CaptureService
import fr.bastien.assistant.data.local.ActionLogEntity
import fr.bastien.assistant.data.local.AssistantDatabase
import fr.bastien.assistant.data.local.CaptureEntity
import fr.bastien.assistant.data.local.TaskEntity
import fr.bastien.assistant.domain.model.CaptureStatus
import fr.bastien.assistant.domain.model.SyncStatus
import fr.bastien.assistant.domain.model.TaskType
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Passe les nouvelles captures à l'IA et range le résultat.
 *
 * - La capture et sa tâche brute sont déjà enregistrées : l'IA ne peut rien faire perdre.
 * - Le texte d'origine n'est jamais modifié.
 * - Si l'utilisateur a touché la tâche entre-temps, on respecte son geste : la tâche part telle quelle.
 * - Panne passagère : exception, on réessaiera. Échec définitif : la tâche brute part telle quelle.
 */
class InterpretationEngine(
    private val db: AssistantDatabase,
    private val interpreter: TaskInterpreter,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao = db.dao()

    /** Traite les captures en attente. Renvoie le nombre de captures interprétées par l'IA. */
    suspend fun processPending(): Int {
        var interpreted = 0
        for (capture in dao.capturesToInterpret(BATCH)) {
            val raw = dao.tasksForCapture(capture.id).minByOrNull { it.createdAt }
            when {
                // Capture d'avant l'IA, ou déjà prise en main par l'utilisateur : rien à demander à l'IA.
                raw == null || raw.syncStatus != SyncStatus.HELD || touchedByUser(capture, raw) -> finishWithoutAi(capture)
                else -> {
                    val result = try {
                        interpreter.interpret(capture.originalText, ZonedDateTime.ofInstant(Instant.ofEpochMilli(clock()), zone()))
                    } catch (e: InterpreterException) {
                        if (e.isTransient) throw e
                        fail(capture, e.message ?: "erreur ${e.status}")
                        continue
                    }
                    applyResult(capture, raw, result)
                    interpreted++
                }
            }
        }
        return interpreted
    }

    /** Après trop d'échecs passagers : tout ce qui attend part tel quel, rien ne reste bloqué. */
    suspend fun giveUp(reason: String) {
        for (capture in dao.capturesToInterpret(Int.MAX_VALUE)) fail(capture, reason)
        dao.releaseAllHeld()
    }

    /** Renommée, cochée ou supprimée avant l'analyse : on respecte le geste de l'utilisateur. */
    private fun touchedByUser(capture: CaptureEntity, raw: TaskEntity): Boolean =
        raw.deletedAt != null || raw.done || raw.title != CaptureService.titleFrom(capture.originalText)

    private suspend fun finishWithoutAi(capture: CaptureEntity) {
        db.withTransaction {
            dao.releaseHeld(capture.id)
            dao.setCaptureStatus(capture.id, CaptureStatus.PROCESSED, null, clock())
        }
    }

    private suspend fun fail(capture: CaptureEntity, reason: String) {
        val now = clock()
        db.withTransaction {
            dao.releaseHeld(capture.id)
            dao.setCaptureStatus(capture.id, CaptureStatus.ERROR, reason.take(300), now)
            log(capture.id, now, "AI_FAILED", "${reason.take(200)} — capture envoyée telle quelle")
        }
    }

    private suspend fun applyResult(capture: CaptureEntity, raw: TaskEntity, tasks: List<InterpretedTask>) {
        val now = clock()
        db.withTransaction {
            val current = dao.getTask(raw.id)
            val untouched = current != null && current.updatedAt == raw.updatedAt &&
                current.deletedAt == null && current.syncStatus == SyncStatus.HELD
            if (tasks.isEmpty() || !untouched) {
                dao.releaseHeld(capture.id)
                val status = if (tasks.isEmpty()) CaptureStatus.TO_CLARIFY else CaptureStatus.PROCESSED
                dao.setCaptureStatus(capture.id, status, null, now)
                log(capture.id, now, "AI_SKIPPED", if (tasks.isEmpty()) "aucune tâche proposée" else "tâche modifiée entre-temps")
                return@withTransaction
            }
            tasks.forEachIndexed { index, task ->
                // Les idées restent dans Assistant ; les tâches partent vers To Do.
                val sync = if (task.type == TaskType.IDEA) SyncStatus.LOCAL_ONLY else SyncStatus.WAITING
                if (index == 0) {
                    dao.applyInterpretation(
                        taskId = raw.id,
                        title = task.title,
                        type = task.type,
                        category = task.category,
                        dueDate = task.dueDate,
                        confidence = task.confidence,
                        needsClarification = task.needsClarification,
                        question = task.clarificationQuestion,
                        syncStatus = sync,
                        now = now,
                        expectedUpdatedAt = raw.updatedAt,
                    )
                } else {
                    dao.insertTask(
                        TaskEntity(
                            id = newId(),
                            captureId = capture.id,
                            type = task.type,
                            title = task.title,
                            dueDate = task.dueDate,
                            category = task.category,
                            confidence = task.confidence,
                            needsClarification = task.needsClarification,
                            clarificationQuestion = task.clarificationQuestion,
                            // Même moment que la capture (+ index) : les tâches restent groupées dans l'historique.
                            createdAt = raw.createdAt + index,
                            updatedAt = now,
                            syncStatus = sync,
                        ),
                    )
                }
            }
            val status = if (tasks.any { it.needsClarification }) CaptureStatus.TO_CLARIFY else CaptureStatus.PROCESSED
            dao.setCaptureStatus(capture.id, status, null, now)
            log(
                capture.id,
                now,
                "AI_INTERPRETED",
                tasks.joinToString(" | ") { "${it.type.name} ${it.category.name} \"${it.title}\"" + (it.dueDate?.let { d -> " pour $d" } ?: "") },
            )
        }
    }

    private suspend fun log(captureId: String, now: Long, action: String, detail: String?) {
        dao.insertLog(ActionLogEntity(captureId = captureId, timestamp = now, action = action, detail = detail?.take(1000)))
    }

    private companion object {
        const val BATCH = 20
    }
}
