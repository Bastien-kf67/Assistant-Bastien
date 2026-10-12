package fr.bastien.assistant.domain.model

/** État d'une capture. Au Jalon 1, une capture reste CAPTURED. */
enum class CaptureStatus { CAPTURED, PROCESSING, PROCESSED, TO_CLARIFY, ERROR, ARCHIVED }

/** Une capture donne des tâches ou des idées (les idées n'iront pas dans To Do). */
enum class TaskType { TASK, IDEA }

/** Classement invisible pour l'utilisateur. */
enum class Category { PRO, PERSONAL, UNKNOWN }

/** État de synchronisation d'une tâche avec un outil externe (To Do au Jalon 2). */
enum class SyncStatus { LOCAL_ONLY, WAITING, SYNCED, ERROR }
