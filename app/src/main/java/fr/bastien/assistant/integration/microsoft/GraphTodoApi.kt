package fr.bastien.assistant.integration.microsoft

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.time.Instant

/** Accès à Microsoft To Do par Microsoft Graph. Le jeton n'est jamais écrit dans un journal. */
class GraphTodoApi(
    private val http: OkHttpClient,
    private val accessToken: String,
) : TodoApi {

    private val base = "https://graph.microsoft.com/v1.0".toHttpUrl()
    private val json = "application/json; charset=utf-8".toMediaType()

    private fun url(vararg segments: String): HttpUrl =
        base.newBuilder().apply { segments.forEach { addPathSegment(it) } }.build()

    override suspend fun findOrCreateList(name: String): String {
        var next: String? = url("me", "todo", "lists").toString()
        while (next != null) {
            val page = call("GET", next)
            val lists = page.optJSONArray("value")
            if (lists != null) {
                for (i in 0 until lists.length()) {
                    val list = lists.getJSONObject(i)
                    if (list.optString("displayName") == name) return list.getString("id")
                }
            }
            next = page.optString("@odata.nextLink").ifBlank { null }
        }
        return call("POST", url("me", "todo", "lists").toString(), JSONObject().put("displayName", name))
            .getString("id")
    }

    override suspend fun createTask(listId: String, title: String, completed: Boolean): String {
        val body = JSONObject().put("title", title).put("status", status(completed))
        return call("POST", url("me", "todo", "lists", listId, "tasks").toString(), body).getString("id")
    }

    override suspend fun updateTask(listId: String, taskId: String, title: String, completed: Boolean) {
        val body = JSONObject().put("title", title).put("status", status(completed))
        call("PATCH", url("me", "todo", "lists", listId, "tasks", taskId).toString(), body)
    }

    override suspend fun deleteTask(listId: String, taskId: String) {
        try {
            call("DELETE", url("me", "todo", "lists", listId, "tasks", taskId).toString())
        } catch (e: TodoApiException) {
            if (e.status != 404) throw e
        }
    }

    override suspend fun delta(listId: String, deltaLink: String?): DeltaResult {
        var next = deltaLink ?: url("me", "todo", "lists", listId, "tasks", "delta").toString()
        val items = mutableListOf<RemoteTask>()
        while (true) {
            val page = call("GET", next)
            val values = page.optJSONArray("value")
            if (values != null) {
                for (i in 0 until values.length()) items += parseTask(values.getJSONObject(i))
            }
            val more = page.optString("@odata.nextLink")
            if (more.isNotBlank()) {
                next = more
            } else {
                return DeltaResult(items, page.optString("@odata.deltaLink").ifBlank { null })
            }
        }
    }

    /** Nom du compte connecté, pour l'afficher dans l'appli. */
    suspend fun accountName(): String? {
        val me = call("GET", url("me").toString())
        return me.optString("userPrincipalName").ifBlank { me.optString("displayName") }.ifBlank { null }
    }

    private fun parseTask(obj: JSONObject) = RemoteTask(
        id = obj.getString("id"),
        title = obj.optString("title"),
        completed = obj.optString("status") == "completed",
        modifiedAt = parseInstant(obj.optString("lastModifiedDateTime")),
        removed = obj.has("@removed"),
    )

    private fun status(completed: Boolean) = if (completed) "completed" else "notStarted"

    private suspend fun call(method: String, url: String, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/json")
                .method(method, body?.toString()?.toRequestBody(json))
                .build()
            try {
                http.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw TodoApiException(response.code, errorMessage(text))
                    if (text.isBlank()) JSONObject() else JSONObject(text)
                }
            } catch (e: IOException) {
                throw TodoApiException(0, "Réseau indisponible (${e.javaClass.simpleName})")
            } catch (e: JSONException) {
                throw TodoApiException(502, "Réponse Microsoft illisible")
            }
        }

    private fun errorMessage(text: String): String = try {
        val error = JSONObject(text).getJSONObject("error")
        "${error.optString("code")} : ${error.optString("message")}".take(300)
    } catch (e: JSONException) {
        text.take(200)
    }

    private fun parseInstant(value: String): Long? =
        if (value.isBlank()) null else runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
}
