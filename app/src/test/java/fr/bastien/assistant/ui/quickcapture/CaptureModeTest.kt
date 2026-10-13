package fr.bastien.assistant.ui.quickcapture

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CaptureModeTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun parlerShortcutOpensVoice() {
        val intent = Intent(Intent.ACTION_MAIN).setComponent(ComponentName(context, "fr.bastien.assistant.Parler"))
        assertEquals(CaptureMode.VOICE, QuickCaptureActivity.modeOf(intent))
    }

    @Test
    fun digitalAssistantOpensVoice() {
        assertEquals(CaptureMode.VOICE, QuickCaptureActivity.modeOf(Intent(Intent.ACTION_ASSIST)))
    }

    @Test
    fun widgetButtonsKeepTheirMode() {
        assertEquals(CaptureMode.TEXT, QuickCaptureActivity.modeOf(QuickCaptureActivity.intent(context, CaptureMode.TEXT)))
        assertEquals(CaptureMode.VOICE, QuickCaptureActivity.modeOf(QuickCaptureActivity.intent(context, CaptureMode.VOICE)))
    }

    @Test
    fun defaultEntryOpensKeyboard() {
        val intent = Intent(Intent.ACTION_MAIN).setComponent(ComponentName(context, "fr.bastien.assistant.Noter"))
        assertEquals(CaptureMode.TEXT, QuickCaptureActivity.modeOf(intent))
    }
}
