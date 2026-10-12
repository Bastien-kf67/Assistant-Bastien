package fr.bastien.assistant.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import fr.bastien.assistant.capture.CaptureSource
import fr.bastien.assistant.domain.model.CaptureStatus
import fr.bastien.assistant.domain.model.Category
import fr.bastien.assistant.domain.model.SyncStatus
import fr.bastien.assistant.domain.model.TaskType

/** Ce que l'utilisateur a saisi ou dicté. [originalText] n'est jamais modifié. */
@Entity(tableName = "captures")
data class CaptureEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val originalText: String,
    val source: CaptureSource,
    val createdAt: Long,
    val updatedAt: Long,
    val status: CaptureStatus,
    val errorMessage: String? = null,
)

/** Ce qu'il faut faire, déduit d'une capture. Une capture peut donner plusieurs tâches. */
@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = CaptureEntity::class,
            parentColumns = ["id"],
            childColumns = ["captureId"],
        ),
    ],
    indices = [Index("captureId"), Index("createdAt")],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val captureId: String,
    val type: TaskType,
    val title: String,
    /** Date ISO (aaaa-mm-jj) ou null : jamais de date inventée. */
    val dueDate: String? = null,
    val category: Category = Category.UNKNOWN,
    val importance: Int? = null,
    val confidence: Double? = null,
    val needsClarification: Boolean = false,
    val clarificationQuestion: String? = null,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val externalProvider: String? = null,
    val externalId: String? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
)

/** Journal des opérations. Ne contient jamais de jeton, clé, mot de passe ni cookie. */
@Entity(tableName = "action_log", indices = [Index("captureId")])
data class ActionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val captureId: String?,
    val timestamp: Long,
    val action: String,
    val detail: String?,
)
