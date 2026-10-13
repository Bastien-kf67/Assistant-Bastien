package fr.bastien.assistant

import android.content.Context
import android.os.Build
import java.io.File
import java.time.Instant

/**
 * Phase de test : si l'appli plante, le rapport d'erreur est gardé sur le téléphone
 * et proposé à la prochaine ouverture (bouton « Copier »), pour pouvoir le transmettre.
 * Rien n'est envoyé automatiquement. Le rapport ne contient ni jeton ni contenu de capture.
 */
object CrashReporter {

    private const val FILE_NAME = "dernier_plantage.txt"
    private const val MAX_LENGTH = 12_000

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { file(appContext).writeText(report(thread, error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? =
        runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private fun report(thread: Thread, error: Throwable): String = buildString {
        appendLine("Assistant ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Date : ${Instant.now()}")
        appendLine("Fil : ${thread.name}")
        appendLine()
        append(error.stackTraceToString())
    }.take(MAX_LENGTH)
}
