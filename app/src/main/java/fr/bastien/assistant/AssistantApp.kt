package fr.bastien.assistant

import android.app.Application
import androidx.work.Configuration
import fr.bastien.assistant.capture.CaptureService
import fr.bastien.assistant.capture.CaptureSource
import fr.bastien.assistant.data.DraftStore
import fr.bastien.assistant.data.TaskService
import fr.bastien.assistant.data.local.AssistantDatabase
import fr.bastien.assistant.integration.microsoft.MicrosoftAuth
import fr.bastien.assistant.integration.microsoft.SecureStore
import fr.bastien.assistant.integration.microsoft.TodoSyncState
import fr.bastien.assistant.worker.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Démarrage volontairement léger : tout est créé à la première utilisation.
 * WorkManager n'est initialisé qu'au premier envoi, pour ne pas ralentir l'écran de saisie.
 */
class AssistantApp : Application(), Configuration.Provider {

    /** Portée qui survit aux écrans : une écriture lancée va au bout même si l'écran se ferme. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { AssistantDatabase.create(this) }
    val draftStore by lazy { DraftStore(this) }

    val todoState by lazy { TodoSyncState(this) }
    val syncScheduler by lazy { SyncScheduler(this, todoState) }
    val microsoftAuth by lazy { MicrosoftAuth(this, SecureStore(this)) }
    val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** Une seule synchro To Do à la fois, quel que soit ce qui l'a déclenchée. */
    val syncLock = Mutex()

    val captureService by lazy {
        CaptureService(database, onSaved = { source ->
            // Les tâches importées de To Do y sont déjà : inutile de relancer un envoi.
            if (source != CaptureSource.TODO_IMPORT) syncScheduler.requestSync()
        })
    }
    val taskService by lazy { TaskService(database, onChanged = { syncScheduler.requestSync() }) }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
