package fr.bastien.assistant.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import fr.bastien.assistant.domain.model.SyncStatus
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
            syncStatus = CASE WHEN syncStatus = 'LOCAL_ONLY' THEN 'LOCAL_ONLY' ELSE 'WAITING' END
        WHERE id = :taskId
        """,
    )
    suspend fun setDone(taskId: String, done: Boolean, doneAt: Long?, now: Long): Int

    @Query("SELECT * FROM action_log WHERE captureId = :captureId ORDER BY timestamp ASC, id ASC")
    suspend fun logForCapture(captureId: String): List<ActionLogEntity>

    // --- Synchronisation avec un outil externe (Microsoft To Do) ---

    @Query("SELECT * FROM tasks WHERE syncStatus = 'WAITING' AND deletedAt IS NULL AND type = 'TASK' ORDER BY createdAt ASC")
    suspend fun tasksToPush(): List<TaskEntity>

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

    /** Tâche supprimée dans To Do : elle sort de l'historique sans être effacée de la base. */
    @Query("UPDATE tasks SET deletedAt = :now, updatedAt = :now, syncStatus = 'SYNCED' WHERE id = :taskId AND deletedAt IS NULL")
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
