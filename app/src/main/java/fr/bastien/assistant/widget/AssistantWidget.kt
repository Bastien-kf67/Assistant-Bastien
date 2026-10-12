package fr.bastien.assistant.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import fr.bastien.assistant.ui.quickcapture.CaptureMode
import fr.bastien.assistant.ui.quickcapture.QuickCaptureActivity

/** Widget d'accueil : deux boutons, rien d'autre. Couleurs du téléphone, clair et sombre. */
class AssistantWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val writeIntent = QuickCaptureActivity.intent(context, CaptureMode.TEXT)
        val voiceIntent = QuickCaptureActivity.intent(context, CaptureMode.VOICE)
        provideContent {
            GlanceTheme {
                WidgetContent(writeIntent, voiceIntent)
            }
        }
    }
}

class AssistantWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AssistantWidget()
}

@Composable
private fun WidgetContent(writeIntent: Intent, voiceIntent: Intent) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetButton("✍  Écrire", writeIntent, GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(6.dp))
        WidgetButton("🎙  Parler", voiceIntent, GlanceModifier.defaultWeight())
    }
}

@Composable
private fun WidgetButton(label: String, intent: Intent, modifier: GlanceModifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .cornerRadius(18.dp)
            .background(GlanceTheme.colors.primaryContainer)
            .clickable(actionStartActivity(intent)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = GlanceTheme.colors.onPrimaryContainer,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}
