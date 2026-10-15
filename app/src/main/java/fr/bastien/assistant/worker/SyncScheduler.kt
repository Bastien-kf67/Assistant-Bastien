package fr.bastien.assistant.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import fr.bastien.assistant.integration.microsoft.TodoConfig
import fr.bastien.assistant.integration.microsoft.TodoSyncState
import java.util.concurrent.TimeUnit

/**
 * Déclenche la synchro To Do : juste après une capture ou un coché, et toutes les 15 minutes
 * pour récupérer ce qui a été coché dans To Do. Rien ne part sans réseau : Android attend qu'il revienne.
 */
class SyncScheduler(private val context: Context, private val state: TodoSyncState) {

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun requestSync() {
        if (!TodoConfig.isConfigured || !state.connected) return
        val request = OneTimeWorkRequestBuilder<TodoSyncWorker>()
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // À la suite de la synchro en cours, jamais à sa place : un envoi n'est jamais coupé au milieu.
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Analyse par l'IA des nouvelles captures (puis envoi vers To Do, déclenché par l'analyse). */
    fun requestInterpretation() {
        val request = OneTimeWorkRequestBuilder<InterpretWorker>()
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(INTERPRET, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun start() {
        val periodic = PeriodicWorkRequestBuilder<TodoSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(network)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodic)
        requestSync()
    }

    fun stop() {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(PERIODIC)
            cancelUniqueWork(ONE_TIME)
        }
    }

    private companion object {
        const val ONE_TIME = "todo-sync"
        const val PERIODIC = "todo-sync-periodic"
        const val INTERPRET = "ai-interpret"
    }
}
