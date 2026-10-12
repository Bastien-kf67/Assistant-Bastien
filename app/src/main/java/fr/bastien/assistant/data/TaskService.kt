package fr.bastien.assistant.data

import androidx.room.withTransaction
import fr.bastien.assistant.data.local.ActionLogEntity
import fr.bastien.assistant.data.local.AssistantDatabase

/** Opérations sur les tâches existantes (cocher, décocher). */
class TaskService(
    private val db: AssistantDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.dao()

    suspend fun setDone(taskId: String, done: Boolean) {
        val now = clock()
        db.withTransaction {
            val task = dao.getTask(taskId) ?: return@withTransaction
            if (task.done == done) return@withTransaction
            dao.setDone(taskId, done, if (done) now else null, now)
            dao.insertLog(
                ActionLogEntity(
                    captureId = task.captureId,
                    timestamp = now,
                    action = if (done) "TASK_DONE" else "TASK_REOPENED",
                    detail = null,
                ),
            )
        }
    }
}
