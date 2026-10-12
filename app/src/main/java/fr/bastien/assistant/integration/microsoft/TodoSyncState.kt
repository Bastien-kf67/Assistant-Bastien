package fr.bastien.assistant.integration.microsoft

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** État affiché dans l'appli. Ne contient aucun jeton : ceux-ci sont dans [SecureStore]. */
data class TodoStatus(
    val connected: Boolean = false,
    val accountName: String? = null,
    val lastSyncAt: Long? = null,
    val lastError: String? = null,
    val needsReconnect: Boolean = false,
)

/** État de la synchro To Do, partagé entre l'appli et le travail en arrière-plan. */
class TodoSyncState(context: Context) : SyncCursor {

    private val prefs = context.getSharedPreferences("todo_sync", Context.MODE_PRIVATE)
    private val _status = MutableStateFlow(read())
    val status: StateFlow<TodoStatus> = _status.asStateFlow()

    val connected: Boolean
        get() = _status.value.connected

    override var listId: String?
        get() = prefs.getString(KEY_LIST_ID, null)
        set(value) {
            prefs.edit().putString(KEY_LIST_ID, value).commit()
        }

    override var deltaLink: String?
        get() = prefs.getString(KEY_DELTA, null)
        set(value) {
            prefs.edit().putString(KEY_DELTA, value).commit()
        }

    fun markConnected(accountName: String?) = update {
        putBoolean(KEY_CONNECTED, true)
        putString(KEY_ACCOUNT, accountName)
        putBoolean(KEY_RECONNECT, false)
        remove(KEY_ERROR)
    }

    /** Garde la liste et le point de reprise : une reconnexion au même compte continue là où on en était. */
    fun markDisconnected() = update {
        putBoolean(KEY_CONNECTED, false)
        remove(KEY_ACCOUNT)
        putBoolean(KEY_RECONNECT, false)
        remove(KEY_ERROR)
    }

    fun markNeedsReconnect() = update { putBoolean(KEY_RECONNECT, true) }

    fun recordSuccess(at: Long) = update {
        putLong(KEY_LAST_SYNC, at)
        remove(KEY_ERROR)
        putBoolean(KEY_RECONNECT, false)
    }

    fun recordError(message: String) = update { putString(KEY_ERROR, message.take(300)) }

    private fun update(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).commit()
        _status.value = read()
    }

    private fun read() = TodoStatus(
        connected = prefs.getBoolean(KEY_CONNECTED, false),
        accountName = prefs.getString(KEY_ACCOUNT, null),
        lastSyncAt = prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0L },
        lastError = prefs.getString(KEY_ERROR, null),
        needsReconnect = prefs.getBoolean(KEY_RECONNECT, false),
    )

    private companion object {
        const val KEY_CONNECTED = "connected"
        const val KEY_ACCOUNT = "account"
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_ERROR = "last_error"
        const val KEY_RECONNECT = "needs_reconnect"
        const val KEY_LIST_ID = "list_id"
        const val KEY_DELTA = "delta_link"
    }
}
