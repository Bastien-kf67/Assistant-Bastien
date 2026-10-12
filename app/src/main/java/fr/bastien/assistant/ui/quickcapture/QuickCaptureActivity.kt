package fr.bastien.assistant.ui.quickcapture

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import fr.bastien.assistant.ui.theme.AssistantTheme

enum class CaptureMode { TEXT, VOICE }

/**
 * Écran de saisie ouvert par le widget : champ prêt, clavier affiché, Entrée valide, fermeture automatique.
 */
class QuickCaptureActivity : ComponentActivity() {

    private val viewModel: QuickCaptureViewModel by viewModels { QuickCaptureViewModel.Factory }

    private val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        viewModel.onVoiceResult(result.resultCode, spoken)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null && modeOf(intent) == CaptureMode.VOICE) startVoice()

        setContent {
            AssistantTheme {
                QuickCaptureScreen(
                    viewModel = viewModel,
                    onCancel = {
                        viewModel.cancel()
                        close()
                    },
                    onFinished = ::close,
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Filet de sécurité : certains téléphones n'affichent le clavier qu'une fois la fenêtre active.
        if (hasFocus && viewModel.phase == Phase.EDITING) {
            WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.ime())
        }
    }

    private fun startVoice() {
        viewModel.onVoiceStarted()
        val voiceIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Je t'écoute")
            // Jalon 1 : rien ne quitte le téléphone, la dictée doit se faire sur l'appareil.
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        try {
            voiceLauncher.launch(voiceIntent)
        } catch (e: ActivityNotFoundException) {
            viewModel.onVoiceUnavailable()
        }
    }

    private fun close() {
        finishAndRemoveTask()
    }

    companion object {
        private const val EXTRA_MODE = "fr.bastien.assistant.extra.MODE"

        private fun modeOf(intent: Intent?): CaptureMode =
            intent?.getStringExtra(EXTRA_MODE)?.let { runCatching { CaptureMode.valueOf(it) }.getOrNull() }
                ?: CaptureMode.TEXT

        /** Chaque appui repart d'un écran neuf ; le brouillon éventuel est restauré. */
        fun intent(context: Context, mode: CaptureMode): Intent =
            Intent(context, QuickCaptureActivity::class.java)
                .setData(Uri.parse("assistant://capture/${mode.name.lowercase()}"))
                .putExtra(EXTRA_MODE, mode.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
