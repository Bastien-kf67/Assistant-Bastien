package fr.bastien.assistant.integration.microsoft

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import org.json.JSONException
import kotlin.coroutines.resume

sealed interface TokenResult {
    data class Ok(val accessToken: String) : TokenResult
    data object NotConnected : TokenResult
    /** Microsoft refuse le renouvellement : il faut se reconnecter (mot de passe changé, accès retiré…). */
    data object NeedsReconnect : TokenResult
    /** Panne passagère (réseau) : on réessaiera. */
    data class Transient(val reason: String) : TokenResult
}

sealed interface ConnectResult {
    data class Ok(val accessToken: String) : ConnectResult
    data class Failed(val message: String) : ConnectResult
}

/**
 * Connexion Microsoft par OAuth (code + PKCE) : l'appli ne voit jamais le mot de passe.
 * Les jetons sont gardés chiffrés dans [SecureStore].
 */
class MicrosoftAuth(context: Context, private val store: SecureStore) {

    private val appContext = context.applicationContext
    private val service by lazy { AuthorizationService(appContext) }
    private val mutex = Mutex()

    private val configuration by lazy {
        val authority = "https://login.microsoftonline.com/${TodoConfig.TENANT_ID}/oauth2/v2.0"
        AuthorizationServiceConfiguration(Uri.parse("$authority/authorize"), Uri.parse("$authority/token"))
    }

    fun isConnected(): Boolean = load()?.refreshToken != null

    /** Écran Microsoft de connexion, à lancer depuis l'appli. */
    fun authorizationIntent(): Intent {
        val request = AuthorizationRequest.Builder(
            configuration,
            TodoConfig.CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(TodoConfig.REDIRECT_URI),
        )
            .setScopes(TodoConfig.SCOPES)
            .setPrompt("select_account")
            .build()
        return service.getAuthorizationRequestIntent(request)
    }

    /** Termine la connexion après le retour de l'écran Microsoft. */
    suspend fun completeAuthorization(data: Intent?): ConnectResult = mutex.withLock {
        val response = data?.let { AuthorizationResponse.fromIntent(it) }
        val error = data?.let { AuthorizationException.fromIntent(it) }
        if (response == null) {
            return@withLock ConnectResult.Failed(describe(error) ?: "Connexion annulée")
        }
        val state = AuthState(response, error)
        val tokenError = suspendCancellableCoroutine<AuthorizationException?> { cont ->
            service.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, exception ->
                state.update(tokenResponse, exception)
                cont.resume(exception)
            }
        }
        val accessToken = state.accessToken
        if (tokenError != null || accessToken == null || state.refreshToken == null) {
            return@withLock ConnectResult.Failed(describe(tokenError) ?: "Microsoft n'a pas délivré d'accès")
        }
        save(state)
        ConnectResult.Ok(accessToken)
    }

    /** Jeton valide, renouvelé si besoin. Le nouveau jeton de renouvellement est aussitôt enregistré. */
    suspend fun freshAccessToken(): TokenResult = mutex.withLock {
        val state = load() ?: return@withLock TokenResult.NotConnected
        if (state.refreshToken == null) return@withLock TokenResult.NotConnected
        suspendCancellableCoroutine<TokenResult> { cont ->
            state.performActionWithFreshTokens(service) { accessToken, _, exception ->
                save(state)
                val result = when {
                    exception == null && accessToken != null -> TokenResult.Ok(accessToken)
                    exception?.type == AuthorizationException.TYPE_OAUTH_TOKEN_ERROR -> TokenResult.NeedsReconnect
                    else -> TokenResult.Transient(describe(exception) ?: "inconnu")
                }
                cont.resume(result)
            }
        }
    }

    fun disconnect() {
        store.put(KEY_STATE, null)
    }

    private fun load(): AuthState? {
        val json = store.get(KEY_STATE) ?: return null
        return try {
            AuthState.jsonDeserialize(json)
        } catch (e: JSONException) {
            null
        }
    }

    private fun save(state: AuthState) {
        store.put(KEY_STATE, state.jsonSerializeString())
    }

    private fun describe(error: AuthorizationException?): String? =
        error?.let { it.errorDescription ?: it.error ?: "erreur ${it.code}" }?.take(200)

    private companion object {
        const val KEY_STATE = "microsoft_auth_state"
    }
}
