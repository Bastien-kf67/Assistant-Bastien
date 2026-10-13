package fr.bastien.assistant.integration.microsoft

import android.content.Intent
import android.net.Uri
import net.openid.appauth.RedirectUriReceiverActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Le retour de la connexion Microsoft passe par un écran d'AppAuth qui exige un thème AppCompat.
 * Sans le bon thème, l'appli plantait au retour de Microsoft (rapport du 13/10/2026).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthRedirectScreenTest {

    @Test
    fun redirectScreenOpensWithoutCrashing() {
        val redirect = Intent(Intent.ACTION_VIEW, Uri.parse("${TodoConfig.REDIRECT_URI}?error=access_denied&state=test"))
        Robolectric.buildActivity(RedirectUriReceiverActivity::class.java, redirect).setup()
    }
}
