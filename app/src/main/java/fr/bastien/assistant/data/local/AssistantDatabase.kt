package fr.bastien.assistant.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base Assistant : la source de vérité.
 *
 * Règle : jamais de migration destructive (pas de fallbackToDestructiveMigration).
 * Chaque changement de schéma passe par une migration écrite et testée.
 */
@Database(
    entities = [CaptureEntity::class, TaskEntity::class, ActionLogEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AssistantDatabase : RoomDatabase() {

    abstract fun dao(): AssistantDao

    companion object {
        private const val FILE_NAME = "assistant.db"

        fun create(context: Context): AssistantDatabase =
            Room.databaseBuilder(context.applicationContext, AssistantDatabase::class.java, FILE_NAME)
                .addCallback(DurableWritesCallback)
                .build()
    }

    /** Écriture disque complète à chaque validation : une capture confirmée survit même à une coupure. */
    private object DurableWritesCallback : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            db.query("PRAGMA synchronous = FULL").close()
        }
    }
}
