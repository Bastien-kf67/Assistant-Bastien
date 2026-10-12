package fr.bastien.assistant.integration.microsoft

import androidx.room.withTransaction
import fr.bastien.assistant.capture.CaptureResult
import fr.bastien.assistant.capture.CaptureService
import fr.bastien.assistant.capture.CaptureSource
import fr.bastien.assistant.data.local.ActionLogEntity
import fr.bastien.assistant.data.local.AssistantDatabase
import fr.bastien.assistant.data.local.TaskEntity
import fr.bastien.assistant.domain.model.SyncStatus

/** Où en est la synchro : la liste utilisée et le point de reprise des changements To Do. */
interface SyncCursor {
    var listId: String?
    var deltaLink: String?
}

data class SyncReport(
    var sent: Int = 0,
    var updated: Int = 0,
    var pulled: Int = 0,
    var imported: Int = 0,
    var errors: Int = 0,
)

/**
 * Synchronise la base Assistant (source de vérité) avec une liste To Do.
 *
 * 1. Récupère ce qui a changé dans To Do (coché, modifié, supprimé, ajouté).
 * 2. Envoie ce qui a changé dans Assistant (nouvelles tâches, coché ou décoché).
 *
 * Une modification locale en attente d'envoi l'emporte toujours sur To Do.
 * Une panne passagère est remontée telle quelle : rien n'est modifié, on réessaiera.
 */
class TodoSyncEngine(
    private val db: AssistantDatabase,
    private val api: TodoApi,
    private val cursor: SyncCursor,
    private val captureService: CaptureService,
    private val listName: String = TodoConfig.LIST_NAME,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.dao()

    private class ListGoneException : Exception()

    suspend fun sync(): SyncReport {
        val report = SyncReport()
        repeat(2) { attempt ->
            val listId = cursor.listId ?: api.findOrCreateList(listName).also {
                cursor.listId = it
                cursor.deltaLink = null
            }
            try {
                pull(listId, report)
                push(listId, report)
                return report
            } catch (e: ListGoneException) {
                if (attempt > 0) throw TodoApiException(404, "Liste To Do introuvable")
                // La liste a été supprimée dans To Do : on la recrée et on y renvoie les tâches.
                cursor.listId = null
                cursor.deltaLink = null
                dao.resetExternalLinks(TodoConfig.PROVIDER)
                log(null, "TODO_LIST_RESET", "liste « $listName » introuvable, recréation")
            }
        }
        return report
    }

    // --- 1. Ce qui vient de To Do ---

    private suspend fun pull(listId: String, report: SyncReport) {
        val result = try {
            deltaOrListGone(listId, cursor.deltaLink)
        } catch (e: TodoApiException) {
            // Point de reprise expiré : on repart de l'état complet de la liste.
            if (e.status == 410 && cursor.deltaLink != null) deltaOrListGone(listId, null) else throw e
        }
        for (item in result.items) applyRemote(item, report)
        cursor.deltaLink = result.deltaLink
    }

    private suspend fun deltaOrListGone(listId: String, link: String?): DeltaResult =
        try {
            api.delta(listId, link)
        } catch (e: TodoApiException) {
            if (e.status == 404) throw ListGoneException() else throw e
        }

    private suspend fun applyRemote(item: RemoteTask, report: SyncReport) {
        val now = clock()
        db.withTransaction {
            val local = dao.findByExternalId(item.id)
            when {
                item.removed -> if (local != null && dao.softDeleteFromRemote(local.id, now) > 0) {
                    report.pulled++
                    log(local.captureId, "TODO_REMOTE_DELETED", null)
                }

                local != null -> if (local.title != item.title || local.done != item.completed) {
                    val doneAt = if (item.completed) local.doneAt ?: item.modifiedAt ?: now else null
                    if (dao.applyRemote(local.id, item.title, item.completed, doneAt, now) > 0) {
                        report.pulled++
                        log(local.captureId, "TODO_REMOTE_CHANGE", "fait=${item.completed} titre=\"${item.title}\"")
                    }
                }

                else -> linkOrImport(item, now, report)
            }
            Unit
        }
    }

    private suspend fun linkOrImport(item: RemoteTask, now: Long, report: SyncReport) {
        if (item.title.isBlank()) return
        // Tâche envoyée dont le lien n'a pas pu être noté (appli coupée au mauvais moment) : on relie,
        // l'envoi suivant ajustera l'état si besoin. Évite tout doublon dans To Do.
        val orphan = dao.findUnlinkedByTitle(item.title)
        if (orphan != null) {
            dao.setExternal(orphan.id, TodoConfig.PROVIDER, item.id)
            log(orphan.captureId, "TODO_LINKED", "reliée à une tâche To Do existante")
            return
        }
        // Tâche créée directement dans To Do : elle entre dans Assistant par le même moteur de capture.
        val result = captureService.capture("todo-${item.id}", item.title, CaptureSource.TODO_IMPORT)
        val captureId = when (result) {
            is CaptureResult.Saved -> result.captureId
            is CaptureResult.AlreadySaved -> result.captureId
            CaptureResult.Empty -> return
        }
        val task = dao.tasksForCapture(captureId).firstOrNull() ?: return
        dao.setExternal(task.id, TodoConfig.PROVIDER, item.id)
        if (item.completed) dao.setDone(task.id, true, item.modifiedAt ?: now, now)
        dao.setSyncStatus(task.id, SyncStatus.SYNCED)
        report.imported++
        log(captureId, "TODO_IMPORTED", "fait=${item.completed}")
    }

    // --- 2. Ce qui part vers To Do ---

    private suspend fun push(listId: String, report: SyncReport) {
        for (task in dao.tasksToPush()) {
            try {
                if (task.externalId == null) send(listId, task, report) else update(listId, task, report)
            } catch (e: TodoApiException) {
                when {
                    e.isTransient || e.isAuth -> throw e
                    e.status == 404 && task.externalId == null -> throw ListGoneException()
                    e.status == 404 -> {
                        // Tâche supprimée dans To Do entre-temps.
                        dao.softDeleteFromRemote(task.id, clock())
                        log(task.captureId, "TODO_REMOTE_DELETED", "introuvable à l'envoi")
                    }
                    else -> {
                        dao.setSyncStatusIfUnchanged(task.id, SyncStatus.ERROR, task.updatedAt)
                        report.errors++
                        log(task.captureId, "TODO_ERROR", "code=${e.status} ${e.message.orEmpty().take(200)}")
                    }
                }
            }
        }
    }

    private suspend fun send(listId: String, task: TaskEntity, report: SyncReport) {
        val externalId = api.createTask(listId, task.title, task.done)
        db.withTransaction {
            dao.setExternal(task.id, TodoConfig.PROVIDER, externalId)
            dao.setSyncStatusIfUnchanged(task.id, SyncStatus.SYNCED, task.updatedAt)
            log(task.captureId, "TODO_SENT", "liste=\"$listName\"")
        }
        report.sent++
    }

    private suspend fun update(listId: String, task: TaskEntity, report: SyncReport) {
        api.updateStatus(listId, requireNotNull(task.externalId), task.done)
        db.withTransaction {
            dao.setSyncStatusIfUnchanged(task.id, SyncStatus.SYNCED, task.updatedAt)
            log(task.captureId, "TODO_UPDATED", "fait=${task.done}")
        }
        report.updated++
    }

    private suspend fun log(captureId: String?, action: String, detail: String?) {
        dao.insertLog(ActionLogEntity(captureId = captureId, timestamp = clock(), action = action, detail = detail))
    }
}
