package fr.bastien.assistant.ui.quickcapture

import android.app.Activity
import android.speech.RecognizerIntent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.bastien.assistant.AssistantApp
import fr.bastien.assistant.capture.CaptureService
import fr.bastien.assistant.capture.CaptureSource
import fr.bastien.assistant.data.DraftStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.UUID

enum class Phase { LISTENING, EDITING, SAVING, SAVED }

class QuickCaptureViewModel(
    private val captureService: CaptureService,
    private val draftStore: DraftStore,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val restoredDraft = draftStore.load()

    /** Identifiant de cette saisie : protège contre le double appui, même après un redémarrage. */
    private var requestId: String = restoredDraft?.requestId ?: newId()

    var entry by mutableStateOf(textValue(restoredDraft?.text.orEmpty()))
        private set
    var phase by mutableStateOf(Phase.EDITING)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var savedText by mutableStateOf<String?>(null)
        private set
    var savedByVoice by mutableStateOf(false)
        private set

    init {
        // Brouillon déjà enregistré (appli coupée juste après l'enregistrement) : on repart de zéro.
        restoredDraft?.let { draft ->
            viewModelScope.launch {
                if (captureService.exists(draft.requestId) && phase == Phase.EDITING && entry.text == draft.text) {
                    draftStore.clear()
                    requestId = newId()
                    entry = textValue("")
                }
            }
        }
    }

    fun onFieldChange(next: TextFieldValue) {
        if (phase != Phase.EDITING) return
        when (val input = FieldInput.interpret(entry.text, next.text)) {
            FieldInput.Submit -> submit()
            is FieldInput.Update -> {
                entry = if (input.text == next.text) next else next.copy(text = input.text)
                message = null
                persistDraft()
            }
        }
    }

    fun submit() {
        if (phase != Phase.EDITING) return
        val text = entry.text
        if (text.isBlank()) return
        save(requestId, text, CaptureSource.ANDROID_TEXT)
    }

    /** Annuler = choix explicite de l'utilisateur : le brouillon est effacé. */
    fun cancel() {
        if (phase == Phase.EDITING) draftStore.clear()
    }

    fun onVoiceStarted() {
        phase = Phase.LISTENING
    }

    fun onVoiceResult(resultCode: Int, spoken: String?) {
        if (phase != Phase.LISTENING) return
        phase = Phase.EDITING
        if (resultCode == Activity.RESULT_OK && !spoken.isNullOrBlank()) {
            // La dictée a sa propre capture : un brouillon tapé auparavant reste intact.
            save(newId(), spoken, CaptureSource.ANDROID_VOICE)
        } else {
            message = voiceFailureMessage(resultCode)
        }
    }

    fun onVoiceUnavailable() {
        phase = Phase.EDITING
        message = "Dictée indisponible sur ce téléphone. Tape ton texte."
    }

    private fun save(id: String, text: String, source: CaptureSource) {
        phase = Phase.SAVING
        message = null
        viewModelScope.launch {
            try {
                // L'écriture tourne dans la portée de l'appli : elle va au bout même si l'écran se ferme.
                appScope.async { captureService.capture(id, text, source) }.await()
                if (source == CaptureSource.ANDROID_TEXT) draftStore.clear()
                savedText = text
                savedByVoice = source == CaptureSource.ANDROID_VOICE
                phase = Phase.SAVED
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (source == CaptureSource.ANDROID_VOICE) keepSpokenText(text)
                phase = Phase.EDITING
                message = "Pas enregistré (${e.javaClass.simpleName}). Ton texte est gardé : réessaie."
            }
        }
    }

    /** Une dictée qui n'a pas pu être enregistrée passe dans le champ, pour ne pas la perdre. */
    private fun keepSpokenText(spoken: String) {
        val merged = if (entry.text.isBlank()) spoken else "${entry.text} $spoken"
        entry = textValue(merged)
        persistDraft()
    }

    private fun persistDraft() {
        if (entry.text.isBlank()) draftStore.clear() else draftStore.save(requestId, entry.text)
    }

    private fun voiceFailureMessage(resultCode: Int): String = when (resultCode) {
        Activity.RESULT_CANCELED -> "Dictée interrompue. Tu peux taper ton texte."
        RecognizerIntent.RESULT_NO_MATCH -> "Je n'ai rien compris. Tape ton texte ou réessaie."
        RecognizerIntent.RESULT_NETWORK_ERROR,
        RecognizerIntent.RESULT_SERVER_ERROR,
        -> "La dictée hors ligne n'est pas disponible. Tape ton texte."
        else -> "La dictée n'a pas marché. Tape ton texte."
    }

    companion object {
        private fun newId() = UUID.randomUUID().toString()

        private fun textValue(text: String) = TextFieldValue(text, TextRange(text.length))

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AssistantApp
                QuickCaptureViewModel(app.captureService, app.draftStore, app.appScope)
            }
        }
    }
}
