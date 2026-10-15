package fr.bastien.assistant.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import fr.bastien.assistant.domain.model.CaptureStatus
import fr.bastien.assistant.domain.model.Category
import fr.bastien.assistant.domain.model.SyncStatus
import fr.bastien.assistant.domain.model.TaskType
import kotlinx.coroutines.flow.Flow

@Dao
interface AssistantDao {

    @Insert
    suspend fun insertCapture(capture: CaptureEntity)

    @Insert
    suspend fun insertTask(task: TaskEntity)

    @Insert
    suspend fun insertLog(entry: ActionLogEntity)

    @Query("SELECT * FROM captures WHERE id = :id")
    suspend fun getCapture(id: String): CaptureEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM captures WHERE id = :id)")
    suspend fun captureExists(id: String): Boolean

    @Query("SELECT COUNT(*) FROM captures")
    suspend fun countCaptures(): Int

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTask(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE captureId = :captureId")
    suspend fun tasksForCapture(captureId: String): List<TaskEntity>

    /** Historique : à faire d'abord (plus récentes en haut), puis les faites. */
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL ORDER BY done ASC, createdAt DESC")
    fun observeHistory(): Flow<List<TaskEntity>>

    /** Cocher ou décocher en local : la tâche repart en attente d'envoi (sauf si elle reste locale). */
    @Query(
        """
        UPDATE tasks SET done = :done, doneAt = :doneAt, updatedAt = :now,
            syncStatus = CASE WHEN syncStatus IN ('LOCAL_ONLY', 'HELD') THEN syncStatus ELSE 'WAITING' END
        WHERE id = :taskId
        """,
    )
    suspend fun setDone(taskId: String, done: Boolean, doneAt: Long?, now: Long): Int

    @Query("SELECT * FROM action_log WHERE captureId = :captureId ORDER BY timestamp ASC, id ASC")
    suspend fun logForCapture(captureId: String): List<ActionLogEntity>

    /** Renommer : le titre de la tâche change, le texte d'origine de la capture jamais. */
    @Query(
        """
        UPDATE tasks SET title = :title, updatedAt = :now,
            syncStatus = CASE WHEN syncStatus IN ('LOCAL_ONLY', 'HELD') THEN syncStatus ELSE 'WAITING' END
        WHERE id = :taskId AND deletedAt IS NULL
        """,
    )
    suspend fun rename(taskId: String, title: String, now: Long): Int

    /** Mise à la corbeille. Une tâche déjà envoyée attend que sa suppression parte vers To Do. */
    @Query(
        """
        UPDATE tasks SET deletedAt = :now, updatedAt = :now,
            syncStatus = CASE WHEN externalId IS NOT NULL AND syncStatus != 'LOCAL_ONLY' THEN 'WAITING' ELSE syncStatus END
        WHERE id = :taskId AND deletedAt IS NULL
        """,
    )
    suspend fun moveToTrash(taskId: String, now: Long): Int

    /** Sortie de corbeille : la tâche repart vers To Do (recréée si elle y avait été supprimée). */
    @Query(
        """
        UPDATE tasks SET deletedAt = NULL, updatedAt = :now,
            syncStatus = CASE WHEN syncStatus IN ('LOCAL_ONLY', 'HELD') THEN syncStatus ELSE 'WAITING' END
        WHERE id = :taskId AND deletedAt IS NOT NULL
        """,
    )
    suspend fun restore(taskId: String, now: Long): Int

    /** Corbeille : tâches supprimées depuis [since], les plus récentes en haut. */
    @Query("SELECT * FROM tasks WHERE deletedAt IS NOT NULL AND deletedAt >= :since ORDER BY deletedAt DESC")
    fun observeTrash(since: Long): Flow<List<TaskEntity>>

    // --- Interprétation par l'IA ---

    /** Captures pas encore passées par l'IA. Les tâches importées de To Do n'y passent pas. */
    @Query("SELECT * FROM captures WHERE status = 'CAPTURED' AND source != 'TODO_IMPORT' ORDER BY createdAt ASC LIMIT :limit")
    suspend fun capturesToInterpret(limit: Int): List<CaptureEntity>

    @Query("UPDATE captures SET status = :status, errorMessage = :error, updatedAt = :now WHERE id = :captureId")
    suspend fun setCaptureStatus(captureId: String, status: CaptureStatus, error: String?, now: Long)

    /** Applique le résultat de l'IA, seulement si l'utilisateur n'a pas touché la tâche entre-temps. */
    @Query(
        """
        UPDATE tasks SET title = :title, type = :type, category = :category, dueDate = :dueDate,
            confidence = :confidence, needsClarification = :needsClarification,
            clarificationQuestion = :question, syncStatus = :syncStatus, updatedAt = :now
        WHERE id = :taskId AND updatedAt = :expectedUpdatedAt AND syncStatus = 'HELD'
        """,
    )
    suspend fun applyInterpretation(
        taskId: String,
        title: String,
        type: TaskType,
        category: Category,
        dueDate: String?,
        confidence: Double,
        needsClarification: Boolean,
        question: String?,
        syncStatus: SyncStatus,
        now: Long,
        expectedUpdatedAt: Long,
    ): Int

    /** Libère telle quelle une tâche retenue (IA en échec ou tâche modifiée par l'utilisateur). */
    @Query("UPDATE tasks SET syncStatus = 'WAITING' WHERE captureId = :captureId AND syncStatus = 'HELD'")
    suspend fun releaseHeld(captureId: String): Int

    @Query("UPDATE tasks SET syncStatus = 'WAITING' WHERE syncStatus = 'HELD'")
    suspend fun releaseAllHeld(): Int

    @Query("SELECT COUNT(*) FROM captures WHERE status = 'CAPTURED' AND source != 'TODO_IMPORT'")
    suspend fun countCapturesToInterpret(): Int

    // --- Synchronisation avec un outil externe (Microsoft To Do) ---

    /** À envoyer : nouvelles tâches, modifications, et suppressions de tâches déjà envoyées. */
    @Query(
        """
        SELECT * FROM tasks WHERE syncStatus = 'WAITING' AND type = 'TASK'
            AND (deletedAt IS NULL OR externalId IS NOT NULL)
        ORDER BY createdAt ASC
        """,
    )
    suspend fun tasksToPush(): List<TaskEntity>

    @Query("UPDATE tasks SET externalId = NULL, externalProvider = NULL WHERE id = :taskId")
    suspend fun clearExternal(taskId: String)

    /** Suppression bien partie vers To Do (sauf si la tâche a été restaurée entre-temps). */
    @Query(
        """
        UPDATE tasks SET externalId = NULL, externalProvider = NULL, syncStatus = 'SYNCED'
        WHERE id = :taskId AND updatedAt = :expectedUpdatedAt
        """,
    )
    suspend fun markRemoteDeleted(taskId: String, expectedUpdatedAt: Long): Int

    @Query("SELECT * FROM tasks WHERE externalId = :externalId LIMIT 1")
    suspend fun findByExternalId(externalId: String): TaskEntity?

    /** Tâche jamais reliée à To Do portant ce titre (envoi fait, mais lien perdu avant d'être noté). */
    @Query(
        """
        SELECT * FROM tasks WHERE externalId IS NULL AND syncStatus = 'WAITING' AND deletedAt IS NULL
            AND type = 'TASK' AND title = :title
        ORDER BY createdAt ASC LIMIT 1
        """,
    )
    suspend fun findUnlinkedByTitle(title: String): TaskEntity?

    @Query("UPDATE tasks SET externalProvider = :provider, externalId = :externalId WHERE id = :taskId")
    suspend fun setExternal(taskId: String, provider: String, externalId: String)

    /** Change l'état de synchro seulement si la tâche n'a pas bougé entre-temps (sinon la modification locale gagne). */
    @Query("UPDATE tasks SET syncStatus = :status WHERE id = :taskId AND updatedAt = :expectedUpdatedAt")
    suspend fun setSyncStatusIfUnchanged(taskId: String, status: SyncStatus, expectedUpdatedAt: Long): Int

    @Query("UPDATE tasks SET syncStatus = :status WHERE id = :taskId")
    suspend fun setSyncStatus(taskId: String, status: SyncStatus)

    /** Changement venu de To Do, ignoré si une modification locale attend d'être envoyée. */
    @Query(
        """
        UPDATE tasks SET title = :title, done = :done, doneAt = :doneAt, updatedAt = :now
        WHERE id = :taskId AND syncStatus != 'WAITING'
        """,
    )
    suspend fun applyRemote(taskId: String, title: String, done: Boolean, doneAt: Long?, now: Long): Int

    /** Tâche supprimée dans To Do : elle part dans la corbeille d'Assistant, rien n'est effacé de la base. */
    @Query(
        """
        UPDATE tasks SET deletedAt = :now, updatedAt = :now, syncStatus = 'SYNCED',
            externalId = NULL, externalProvider = NULL
        WHERE id = :taskId AND deletedAt IS NULL
        """,
    )
    suspend fun softDeleteFromRemote(taskId: String, now: Long): Int

    /** À la connexion : les tâches restées locales (Jalon 1) partent aussi vers To Do. */
    @Query(
        """
        UPDATE tasks SET syncStatus = 'WAITING'
        WHERE externalId IS NULL AND deletedAt IS NULL AND type = 'TASK' AND syncStatus = 'LOCAL_ONLY'
        """,
    )
    suspend fun queueUnsentTasks(): Int

    /** La liste To Do a disparu : tout sera renvoyé dans une nouvelle liste. */
    @Query(
        """
        UPDATE tasks SET externalId = NULL, externalProvider = NULL, syncStatus = 'WAITING'
        WHERE externalProvider = :provider AND deletedAt IS NULL
        """,
    )
    suspend fun resetExternalLinks(provider: String): Int

    @Query("UPDATE tasks SET syncStatus = 'WAITING' WHERE syncStatus = 'ERROR' AND deletedAt IS NULL")
    suspend fun retryErrors(): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE syncStatus = :status AND deletedAt IS NULL AND type = 'TASK'")
    fun observeCount(status: SyncStatus): Flow<Int>
}
