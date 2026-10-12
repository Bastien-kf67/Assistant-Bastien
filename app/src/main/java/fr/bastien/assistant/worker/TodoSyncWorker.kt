package fr.bastien.assistant.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import fr.bastien.assistant.AssistantApp
import fr.bastien.assistant.integration.microsoft.GraphTodoApi
import fr.bastien.assistant.integration.microsoft.TodoApiException
import fr.bastien.assistant.integration.microsoft.TodoConfig
import fr.bastien.assistant.integration.microsoft.TodoSyncEngine
import fr.bastien.assistant.integration.microsoft.TokenResult
import kotlinx.coroutines.sync.withLock

/** Une synchro To Do en arrière-plan. Jamais deux en même temps (verrou partagé). */
class TodoSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as AssistantApp
        val state = app.todoState
        if (!TodoConfig.isConfigured || !state.connected) return Result.success()

        val token = when (val result = app.microsoftAuth.freshAccessToken()) {
            is TokenResult.Ok -> result.accessToken
            TokenResult.NotConnected -> {
                state.markNeedsReconnect()
                return Result.success()
            }
            TokenResult.NeedsReconnect -> {
                state.markNeedsReconnect()
                return Result.success()
            }
            is TokenResult.Transient -> {
                state.recordError("Connexion Microsoft impossible pour l'instant (${result.reason})")
                return retryOrGiveUp()
            }
        }

        return app.syncLock.withLock {
            try {
                TodoSyncEngine(app.database, GraphTodoApi(app.httpClient, token), state, app.captureService).sync()
                state.recordSuccess(System.currentTimeMillis())
                Result.success()
            } catch (e: TodoApiException) {
                if (e.isAuth) state.markNeedsReconnect()
                state.recordError(e.message ?: "Erreur To Do ${e.status}")
                if (e.isTransient) retryOrGiveUp() else Result.success()
            }
        }
    }

    /** Après de nombreux essais, on attend la prochaine synchro périodique plutôt que d'insister. */
    private fun retryOrGiveUp(): Result = if (runAttemptCount < 8) Result.retry() else Result.success()
}
