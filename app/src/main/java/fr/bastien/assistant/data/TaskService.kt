package fr.bastien.assistant.data

import androidx.room.withTransaction
import fr.bastien.assistant.data.local.ActionLogEntity
import fr.bastien.assistant.data.local.AssistantDatabase

/** Opérations sur les tâches existantes (cocher, décocher). */
class TaskService(
    private val db: AssistantDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Appelé après une modification locale (ex. : demander un envoi vers To Do). */
    private val onChanged: () -> Unit = {},
) {
    private val dao = db.dao()

    suspend fun setDone(taskId: String, done: Boolean) {
        val now = clock()
        val changed = db.withTransaction {
            val task = dao.getTask(taskId) ?: return@withTransaction false
            if (task.done == done) return@withTransaction false
            dao.setDone(taskId, done, if (done) now else null, now)
            dao.insertLog(
                ActionLogEntity(
                    captureId = task.captureId,
                    timestamp = now,
                    action = if (done) "TASK_DONE" else "TASK_REOPENED",
                    detail = null,
                ),
            )
            true
        }
        if (changed) runCatching { onChanged() }
    }
}
