package fr.bastien.assistant.capture

/** Toutes les portes d'entrée passent par le même [CaptureService]. */
enum class CaptureSource {
    ANDROID_TEXT,
    ANDROID_VOICE,
    TODO_IMPORT,
    ANDROID_SHARE,
    EMAIL,
    OUTLOOK,
    WHATSAPP,
    BUSINESS_APP,
    SYSTEM,
}
