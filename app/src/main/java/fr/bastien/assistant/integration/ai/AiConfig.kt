package fr.bastien.assistant.integration.ai

import android.content.Context
import fr.bastien.assistant.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Modèles proposés dans l'appli. Changer de modèle ne demande aucune autre modification. */
enum class AiModel(val apiId: String, val label: String, val hint: String) {
    HAIKU("claude-haiku-5-5", "Haiku", "rapide, moins de 1 $/mois"),
    SONNET("claude-sonnet-5-5", "Sonnet", "plus fin, environ 5 $/mois"),
}

/**
 * Réglages de l'IA. La clé d'accès vient des secrets GitHub, intégrée à la compilation :
 * elle n'est jamais dans le dépôt. Sans clé, l'IA est simplement désactivée.
 */
class AiConfig(context: Context) {

    private val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
    private val _model = MutableStateFlow(readModel())
    val model: StateFlow<AiModel> = _model.asStateFlow()

    val apiKey: String
        get() = BuildConfig.ANTHROPIC_API_KEY

    val enabled: Boolean
        get() = apiKey.isNotBlank()

    fun setModel(model: AiModel) {
        prefs.edit().putString(KEY_MODEL, model.name).apply()
        _model.value = model
    }

    private fun readModel(): AiModel =
        prefs.getString(KEY_MODEL, null)?.let { runCatching { AiModel.valueOf(it) }.getOrNull() } ?: AiModel.HAIKU

    private companion object {
        const val KEY_MODEL = "model"
    }
}
