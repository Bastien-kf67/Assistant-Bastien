package fr.bastien.assistant

import android.app.Application
import fr.bastien.assistant.capture.CaptureService
import fr.bastien.assistant.data.DraftStore
import fr.bastien.assistant.data.TaskService
import fr.bastien.assistant.data.local.AssistantDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Démarrage volontairement léger : tout est créé à la première utilisation. */
class AssistantApp : Application() {

    /** Portée qui survit aux écrans : une écriture lancée va au bout même si l'écran se ferme. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { AssistantDatabase.create(this) }
    val captureService by lazy { CaptureService(database) }
    val taskService by lazy { TaskService(database) }
    val draftStore by lazy { DraftStore(this) }
}
