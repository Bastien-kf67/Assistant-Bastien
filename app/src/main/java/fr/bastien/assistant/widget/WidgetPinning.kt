package fr.bastien.assistant.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/** Proposer de poser le widget depuis l'appli, quand il n'est pas encore sur l'écran d'accueil. */
object WidgetPinning {

    private fun component(context: Context) = ComponentName(context, AssistantWidgetReceiver::class.java)

    fun isPlaced(context: Context): Boolean =
        AppWidgetManager.getInstance(context).getAppWidgetIds(component(context)).isNotEmpty()

    fun canRequestPin(context: Context): Boolean =
        AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    fun requestPin(context: Context) {
        AppWidgetManager.getInstance(context).requestPinAppWidget(component(context), null, null)
    }
}
