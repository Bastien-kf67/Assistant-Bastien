package fr.bastien.assistant.integration.microsoft

/**
 * Inscription de l'appli dans Microsoft 365 (Entra ID). Ces identifiants ne sont pas secrets :
 * une appli mobile est un « client public », sans mot de passe d'application.
 */
object TodoConfig {
    /** « ID d'application (client) » de l'inscription « Assistant Bastien ». */
    const val CLIENT_ID = ""

    /** « ID de l'annuaire (locataire) » du Microsoft 365 de La Paysagerie. */
    const val TENANT_ID = ""

    /** Doit correspondre à l'URI déclarée dans l'inscription et à appAuthRedirectScheme (build.gradle). */
    const val REDIRECT_URI = "fr.bastien.assistant://auth"

    /** Liste dédiée au test, séparée de la liste quotidienne. */
    const val LIST_NAME = "Assistant (test)"

    /** Valeur de externalProvider pour les tâches reliées à To Do. */
    const val PROVIDER = "MS_TODO"

    val SCOPES = listOf("offline_access", "User.Read", "Tasks.ReadWrite")

    val isConfigured: Boolean
        get() = CLIENT_ID.isNotBlank() && TENANT_ID.isNotBlank()
}
