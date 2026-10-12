package fr.bastien.assistant.ui.quickcapture

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun QuickCaptureScreen(
    viewModel: QuickCaptureViewModel,
    onCancel: () -> Unit,
    onFinished: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            when (viewModel.phase) {
                Phase.LISTENING -> Listening()
                Phase.SAVED -> Saved(viewModel.savedText, viewModel.savedByVoice, onFinished)
                Phase.EDITING, Phase.SAVING -> Editor(viewModel, onCancel)
            }
        }
    }
}

@Composable
private fun Editor(viewModel: QuickCaptureViewModel, onCancel: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val editing = viewModel.phase == Phase.EDITING

    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Ajouter quelque chose", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = viewModel.entry,
            onValueChange = viewModel::onFieldChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = { Text("Ce que tu ne veux pas oublier") },
            enabled = editing,
            minLines = 2,
            maxLines = 6,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
        )
        viewModel.message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel, enabled = editing) { Text("Annuler") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = viewModel::submit,
                enabled = editing && viewModel.entry.text.isNotBlank(),
            ) { Text(if (editing) "Ajouter" else "…") }
        }
    }
}

@Composable
private fun Listening() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Je t'écoute…", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Saved(text: String?, byVoice: Boolean, onFinished: () -> Unit) {
    val view = LocalView.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        keyboard?.hide()
        val feedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(feedback)
        // Une dictée reste affichée un peu plus longtemps, pour relire ce qui a été compris.
        delay(if (byVoice) 1_800 else 650)
        onFinished()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("✓ Ajouté", fontSize = 28.sp, color = MaterialTheme.colorScheme.primary)
        if (byVoice && text != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
