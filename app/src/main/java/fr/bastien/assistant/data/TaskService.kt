package fr.bastien.assistant.data

import androidx.room.withTransaction
import fr.bastien.assistant.capture.CaptureService
import fr.bastien.assistant.data.local.ActionLogEntity
import fr.bastien.assistant.data.local.AssistantDatabase
import fr.bastien.assistant.data.local.TaskEntity

/** Opérations sur les tâches existantes : cocher, renommer, mettre à la corbeille, restaurer. */
class TaskService(
    private val db: AssistantDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Appelé après une modification locale (ex. : demander un envoi vers To Do). */
    private val onChanged: () -> Unit = {},
) {
    private val dao = db.dao()

    suspend fun setDone(taskId: String, done: Boolean) = change(taskId) { task, now ->
        if (task.done == done) return@change null
        dao.setDone(taskId, done, if (done) now else null, now)
        if (done) "TASK_DONE" else "TASK_REOPENED"
    }

    /** Un titre vide est ignoré : pour retirer une tâche, on la met à la corbeille. */
    suspend fun rename(taskId: String, newTitle: String) = change(taskId) { task, now ->
        val title = CaptureService.titleFrom(newTitle)
        if (title.isEmpty() || title == task.title) return@change null
        if (dao.rename(taskId, title, now) == 0) return@change null
        "TASK_RENAMED"
    }

    suspend fun moveToTrash(taskId: String) = change(taskId) { _, now ->
        if (dao.moveToTrash(taskId, now) == 0) null else "TASK_TRASHED"
    }

    suspend fun restore(taskId: String) = change(taskId) { _, now ->
        if (dao.restore(taskId, now) == 0) null else "TASK_RESTORED"
    }

    /**
     * Applique [block] dans une transaction et journalise l'action qu'il renvoie
     * (null = rien n'a changé : ni journal, ni envoi).
     */
    private suspend fun change(
        taskId: String,
        block: suspend (task: TaskEntity, now: Long) -> String?,
    ): Boolean {
        val now = clock()
        val changed = db.withTransaction {
            val task = dao.getTask(taskId) ?: return@withTransaction false
            val action = block(task, now) ?: return@withTransaction false
            dao.insertLog(ActionLogEntity(captureId = task.captureId, timestamp = now, action = action, detail = null))
            true
        }
        if (changed) runCatching { onChanged() }
        return changed
    }
}
