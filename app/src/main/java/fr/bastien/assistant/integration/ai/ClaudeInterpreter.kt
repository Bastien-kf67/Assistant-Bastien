package fr.bastien.assistant.integration.ai

import fr.bastien.assistant.domain.interpreter.InterpretedTask
import fr.bastien.assistant.domain.interpreter.InterpreterException
import fr.bastien.assistant.domain.interpreter.TaskInterpreter
import fr.bastien.assistant.domain.model.Category
import fr.bastien.assistant.domain.model.TaskType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Interprétation par Claude (API Anthropic). La réponse est structurée : l'IA doit remplir
 * le formulaire [InterpretationPrompt.tool], ce qui évite les réponses en texte libre.
 * La clé n'est jamais écrite dans un journal.
 */
class ClaudeInterpreter(
    private val http: OkHttpClient,
    private val apiKey: String,
    private val model: () -> String,
    private val endpoint: String = "https://api.anthropic.com/v1/messages",
) : TaskInterpreter {

    private val json = "application/json; charset=utf-8".toMediaType()

    override suspend fun interpret(text: String, now: ZonedDateTime): List<InterpretedTask> {
        val body = JSONObject()
            .put("model", model())
            .put("max_tokens", 1024)
            .put("system", InterpretationPrompt.system)
            .put("tools", JSONArray().put(InterpretationPrompt.tool))
            .put("tool_choice", JSONObject().put("type", "tool").put("name", InterpretationPrompt.TOOL_NAME))
            .put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user").put("content", InterpretationPrompt.userMessage(text, now)),
                ),
            )
        val response = post(body)
        return parse(response, fallbackTitle = text)
    }

    private suspend fun post(body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(json))
            .build()
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw InterpreterException(response.code, errorMessage(text))
                JSONObject(text)
            }
        } catch (e: IOException) {
            throw InterpreterException(0, "Réseau indisponible (${e.javaClass.simpleName})")
        } catch (e: JSONException) {
            throw InterpreterException(502, "Réponse de l'IA illisible")
        }
    }

    private fun errorMessage(text: String): String = try {
        val error = JSONObject(text).getJSONObject("error")
        "${error.optString("type")} : ${error.optString("message")}".take(300)
    } catch (e: JSONException) {
        text.take(200)
    }

    companion object {
        private val isoDate = Regex("""\d{4}-\d{2}-\d{2}""")

        /** Lit la réponse de l'API. Tout champ douteux est ramené à une valeur prudente. */
        fun parse(response: JSONObject, fallbackTitle: String): List<InterpretedTask> {
            val content = response.optJSONArray("content") ?: throw InterpreterException(502, "Réponse sans contenu")
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                if (block.optString("type") != "tool_use" || block.optString("name") != InterpretationPrompt.TOOL_NAME) continue
                val tasks = block.optJSONObject("input")?.optJSONArray("taches") ?: return emptyList()
                return (0 until tasks.length()).mapNotNull { index ->
                    tasks.optJSONObject(index)?.let { parseTask(it, fallbackTitle) }
                }
            }
            throw InterpreterException(502, "L'IA n'a pas rempli le formulaire")
        }

        private fun parseTask(obj: JSONObject, fallbackTitle: String): InterpretedTask {
            val title = obj.optString("titre").trim().replace(Regex("\\s+"), " ").take(200)
                .ifBlank { fallbackTitle.trim().take(200) }
            val type = if (obj.optString("type") == "IDEE") TaskType.IDEA else TaskType.TASK
            val category = when (obj.optString("categorie")) {
                "PRO" -> Category.PRO
                "IMMOBILIER" -> Category.IMMOBILIER
                "PERSO" -> Category.PERSONAL
                "ADMIN" -> Category.ADMIN
                "PROJETS" -> Category.PROJETS
                else -> Category.UNKNOWN
            }
            val question = obj.optString("question").trim().take(200).ifBlank { null }
            val needsClarification = obj.optBoolean("a_preciser", false)
            return InterpretedTask(
                title = title,
                type = type,
                category = category,
                dueDate = validDate(obj.optString("echeance")),
                confidence = obj.optDouble("confiance", 0.5).takeIf { !it.isNaN() }?.coerceIn(0.0, 1.0) ?: 0.5,
                needsClarification = needsClarification,
                clarificationQuestion = if (needsClarification) question else null,
            )
        }

        /** Une date mal formée ou absurde est ignorée : mieux vaut pas de date qu'une date fausse. */
        private fun validDate(value: String): String? {
            if (!isoDate.matches(value.trim())) return null
            val date = runCatching { LocalDate.parse(value.trim()) }.getOrNull() ?: return null
            return if (date.year in 2020..2100) date.toString() else null
        }
    }
}
