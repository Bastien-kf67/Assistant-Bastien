package fr.bastien.assistant.widget

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import fr.bastien.assistant.ui.quickcapture.CaptureMode
import fr.bastien.assistant.ui.quickcapture.QuickCaptureActivity

/**
 * Tuile « Noter » des réglages rapides : accessible depuis n'importe quelle appli,
 * et depuis l'écran verrouillé (l'écran de saisie s'affiche sans montrer les captures).
 */
class CaptureTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        val intent = QuickCaptureActivity.intent(this, CaptureMode.TEXT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
