package fr.bastien.assistant.domain.model

/**
 * État d'une capture : CAPTURED tant que l'IA ne l'a pas traitée, puis PROCESSED,
 * TO_CLARIFY (traitée mais floue) ou ERROR (IA en échec : la capture brute est partie telle quelle).
 */
enum class CaptureStatus { CAPTURED, PROCESSING, PROCESSED, TO_CLARIFY, ERROR, ARCHIVED }

/** Une capture donne des tâches ou des idées (les idées restent dans Assistant, pas dans To Do). */
enum class TaskType { TASK, IDEA }

/**
 * Case de rangement. Les valeurs sont stockées par leur nom : en ajouter ne demande aucune migration,
 * en renommer ou en supprimer, si.
 */
enum class Category(val label: String) {
    PRO("Pro"),
    IMMOBILIER("Immobilier"),
    PERSONAL("Perso"),
    ADMIN("Admin"),
    PROJETS("Projets"),
    UNKNOWN("À classer"),
}

/**
 * État de synchronisation d'une tâche avec To Do.
 * HELD : retenue le temps que l'IA la retravaille ; rien ne part vers To Do avant.
 */
enum class SyncStatus { LOCAL_ONLY, WAITING, SYNCED, ERROR, HELD }
