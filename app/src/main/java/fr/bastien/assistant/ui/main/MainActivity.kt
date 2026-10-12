package fr.bastien.assistant.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import fr.bastien.assistant.ui.quickcapture.CaptureMode
import fr.bastien.assistant.ui.quickcapture.QuickCaptureActivity
import fr.bastien.assistant.ui.theme.AssistantTheme
import fr.bastien.assistant.widget.WidgetPinning

/** Historique des captures : vérifier, cocher. Ce n'est pas un gestionnaire de tâches complet. */
class MainActivity : ComponentActivity() {

    private val viewModel: HistoryViewModel by viewModels { HistoryViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AssistantTheme {
                HistoryScreen(
                    viewModel = viewModel,
                    onWrite = { startActivity(QuickCaptureActivity.intent(this, CaptureMode.TEXT)) },
                    widgetPlaced = { WidgetPinning.isPlaced(this) },
                    canPinWidget = { WidgetPinning.canRequestPin(this) },
                    onPinWidget = { WidgetPinning.requestPin(this) },
                )
            }
        }
    }
}
