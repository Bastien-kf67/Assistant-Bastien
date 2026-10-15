package fr.bastien.assistant.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import fr.bastien.assistant.AssistantApp
import fr.bastien.assistant.domain.interpreter.InterpreterException
import kotlinx.coroutines.sync.withLock

/**
 * Passe les nouvelles captures à l'IA, puis demande l'envoi vers To Do.
 * Sans clé d'IA, ou après trop d'échecs, les captures partent telles quelles : rien ne reste bloqué.
 */
class InterpretWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as AssistantApp
        val engine = app.interpretationEngine
        return app.interpretLock.withLock {
            try {
                if (!app.aiConfig.enabled) {
                    app.database.dao().releaseAllHeld()
                } else {
                    engine.processPending()
                }
                app.syncScheduler.requestSync()
                Result.success()
            } catch (e: InterpreterException) {
                if (runAttemptCount < MAX_ATTEMPTS) {
                    Result.retry()
                } else {
                    engine.giveUp("IA injoignable après $MAX_ATTEMPTS essais (${e.message})")
                    app.syncScheduler.requestSync()
                    Result.success()
                }
            }
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
