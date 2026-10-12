package fr.bastien.assistant.data

import android.content.Context

/** Brouillon de l'écran de saisie, gardé si la saisie est interrompue (appel, autre appli). */
data class Draft(val requestId: String, val text: String)

class DraftStore(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): Draft? {
        val id = prefs.getString(KEY_REQUEST_ID, null) ?: return null
        val text = prefs.getString(KEY_TEXT, null) ?: return null
        return if (text.isBlank()) null else Draft(id, text)
    }

    fun save(requestId: String, text: String) {
        prefs.edit().putString(KEY_REQUEST_ID, requestId).putString(KEY_TEXT, text).apply()
    }

    /** Effacement immédiat sur le disque, appelé juste après un enregistrement réussi. */
    fun clear() {
        prefs.edit().clear().commit()
    }

    private companion object {
        const val FILE = "quick_capture_draft"
        const val KEY_REQUEST_ID = "request_id"
        const val KEY_TEXT = "text"
    }
}
