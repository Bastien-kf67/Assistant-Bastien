package fr.bastien.assistant.ui.main

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import fr.bastien.assistant.ui.quickcapture.CaptureMode
import fr.bastien.assistant.ui.quickcapture.QuickCaptureActivity
import fr.bastien.assistant.ui.theme.AssistantTheme
import fr.bastien.assistant.widget.WidgetPinning

/**
 * Historique des captures : vérifier, cocher, modifier, connecter To Do.
 * Protégé par l'empreinte (ou le code du téléphone). Noter, lui, ne demande jamais rien.
 */
class MainActivity : FragmentActivity() {

    private val viewModel: HistoryViewModel by viewModels { HistoryViewModel.Factory }
    private lateinit var biometricPrompt: BiometricPrompt
    private var promptShowing = false

    private val microsoftSignIn = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onAuthorizationResult(result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    promptShowing = false
                    viewModel.unlocked = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    promptShowing = false
                }
            },
        )
        setContent {
            AssistantTheme {
                if (viewModel.unlocked) {
                    HistoryScreen(
                        viewModel = viewModel,
                        onWrite = ::openCapture,
                        onConnectTodo = ::connectTodo,
                        widgetPlaced = { WidgetPinning.isPlaced(this) },
                        canPinWidget = { WidgetPinning.canRequestPin(this) },
                        onPinWidget = { WidgetPinning.requestPin(this) },
                    )
                } else {
                    LockedScreen(onUnlock = ::askToUnlock, onWrite = ::openCapture)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        when {
            !canLock() -> viewModel.unlocked = true
            viewModel.unlocked && viewModel.stoppedAt > 0 &&
                SystemClock.elapsedRealtime() - viewModel.stoppedAt > RELOCK_AFTER_MS -> viewModel.unlocked = false
        }
        if (!viewModel.unlocked) askToUnlock()
    }

    override fun onStop() {
        super.onStop()
        viewModel.stoppedAt = SystemClock.elapsedRealtime()
    }

    /** Sans verrouillage d'écran configuré sur le téléphone, l'appli ne peut pas se verrouiller. */
    private fun canLock(): Boolean =
        BiometricManager.from(this).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    private fun askToUnlock() {
        if (promptShowing || viewModel.unlocked) return
        promptShowing = true
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Assistant")
            .setSubtitle("Déverrouiller l'historique")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        biometricPrompt.authenticate(info)
    }

    private fun openCapture() {
        startActivity(QuickCaptureActivity.intent(this, CaptureMode.TEXT))
    }

    private fun connectTodo() {
        try {
            microsoftSignIn.launch(viewModel.authorizationIntent())
        } catch (e: ActivityNotFoundException) {
            viewModel.onAuthorizationUnavailable()
        }
    }

    private companion object {
        /** Empreinte, visage ou, à défaut, le code du téléphone. */
        const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        const val RELOCK_AFTER_MS = 60_000L
    }
}

@Composable
private fun LockedScreen(onUnlock: () -> Unit, onWrite: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Assistant verrouillé", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onUnlock) { Text("Déverrouiller") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onWrite) { Text("✍  Noter sans déverrouiller") }
        }
    }
}
