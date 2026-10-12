package fr.bastien.assistant.ui.main

import android.content.ActivityNotFoundException
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import fr.bastien.assistant.ui.quickcapture.CaptureMode
import fr.bastien.assistant.ui.quickcapture.QuickCaptureActivity
import fr.bastien.assistant.ui.theme.AssistantTheme
import fr.bastien.assistant.widget.WidgetPinning

/** Historique des captures : vérifier, cocher, connecter To Do. Pas un gestionnaire de tâches complet. */
class MainActivity : ComponentActivity() {

    private val viewModel: HistoryViewModel by viewModels { HistoryViewModel.Factory }

    private val microsoftSignIn = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onAuthorizationResult(result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AssistantTheme {
                HistoryScreen(
                    viewModel = viewModel,
                    onWrite = { startActivity(QuickCaptureActivity.intent(this, CaptureMode.TEXT)) },
                    onConnectTodo = ::connectTodo,
                    widgetPlaced = { WidgetPinning.isPlaced(this) },
                    canPinWidget = { WidgetPinning.canRequestPin(this) },
                    onPinWidget = { WidgetPinning.requestPin(this) },
                )
            }
        }
    }

    private fun connectTodo() {
        try {
            microsoftSignIn.launch(viewModel.authorizationIntent())
        } catch (e: ActivityNotFoundException) {
            viewModel.onAuthorizationUnavailable()
        }
    }
}
