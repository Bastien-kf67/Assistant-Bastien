package fr.bastien.assistant.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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

    @Query("UPDATE tasks SET done = :done, doneAt = :doneAt, updatedAt = :now WHERE id = :taskId")
    suspend fun setDone(taskId: String, done: Boolean, doneAt: Long?, now: Long): Int

    @Query("SELECT * FROM action_log WHERE captureId = :captureId ORDER BY timestamp ASC, id ASC")
    suspend fun logForCapture(captureId: String): List<ActionLogEntity>
}
