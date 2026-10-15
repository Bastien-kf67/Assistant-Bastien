package fr.bastien.assistant.integration.ai

import fr.bastien.assistant.domain.interpreter.InterpreterException
import fr.bastien.assistant.domain.model.Category
import fr.bastien.assistant.domain.model.TaskType
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

/** Appel à l'API Claude contre un faux serveur : format de la demande, lecture de la réponse, erreurs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClaudeInterpreterTest {

    private lateinit var server: MockWebServer
    private lateinit var interpreter: ClaudeInterpreter
    private val now = ZonedDateTime.of(2026, 10, 15, 16, 5, 0, 0, ZoneId.of("Europe/Paris"))

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        interpreter = ClaudeInterpreter(
            OkHttpClient(),
            apiKey = "sk-ant-test",
            model = { "claude-haiku-5-5" },
            endpoint = server.url("/v1/messages").toString(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun toolAnswer(tasks: String) = """
        {"id":"msg_1","type":"message","role":"assistant","model":"claude-haiku-5-5",
         "content":[{"type":"tool_use","id":"toolu_1","name":"enregistrer_taches","input":{"taches":$tasks}}],
         "stop_reason":"tool_use"}
    """.trimIndent()

    @Test
    fun sendsAWellFormedRequest() = runBlocking {
        server.enqueue(MockResponse().setBody(toolAnswer("[]")))

        interpreter.interpret("rappeler le garage vendredi", now)

        val request = server.takeRequest()
        assertEquals("sk-ant-test", request.getHeader("x-api-key"))
        assertEquals("2023-06-01", request.getHeader("anthropic-version"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals("claude-haiku-5-5", body.getString("model"))
        assertEquals("enregistrer_taches", body.getJSONObject("tool_choice").getString("name"))
        val message = body.getJSONArray("messages").getJSONObject(0).getString("content")
        assertTrue(message.contains("jeudi 2026-10-15"))
        assertTrue(message.contains("vendredi 2026-10-16"))
        assertTrue(message.contains("rappeler le garage vendredi"))
    }

    @Test
    fun readsSplitTasksAndCleansOddValues() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                toolAnswer(
                    """[
                      {"titre":"Appeler Weber","type":"TACHE","categorie":"PRO","confiance":0.95,"a_preciser":false},
                      {"titre":"Commander les filtres","type":"TACHE","categorie":"PRO","echeance":"2026-10-16","confiance":0.9,"a_preciser":false},
                      {"titre":"Appli bébé : champ libre","type":"IDEE","categorie":"PROJETS","echeance":"vendredi","confiance":2,"a_preciser":false},
                      {"titre":"  ","type":"???","categorie":"AUTRE","confiance":0.2,"a_preciser":true,"question":"De quoi s'agit-il ?"}
                    ]""",
                ),
            ),
        )

        val tasks = interpreter.interpret("appeler weber et commander les filtres", now)

        assertEquals(4, tasks.size)
        assertEquals("Appeler Weber", tasks[0].title)
        assertEquals("2026-10-16", tasks[1].dueDate)
        assertEquals(TaskType.IDEA, tasks[2].type)
        assertEquals(Category.PROJETS, tasks[2].category)
        assertNull(tasks[2].dueDate) // « vendredi » n'est pas une date : ignoré plutôt que deviné
        assertEquals(1.0, tasks[2].confidence, 0.0)
        assertEquals("appeler weber et commander les filtres", tasks[3].title) // titre vide → texte d'origine
        assertEquals(TaskType.TASK, tasks[3].type)
        assertEquals(Category.UNKNOWN, tasks[3].category)
        assertTrue(tasks[3].needsClarification)
        assertEquals("De quoi s'agit-il ?", tasks[3].clarificationQuestion)
    }

    @Test
    fun overloadIsTransient() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(529).setBody("""{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}"""))

        try {
            interpreter.interpret("note", now)
            fail("Erreur attendue")
        } catch (e: InterpreterException) {
            assertTrue(e.isTransient)
            assertTrue(e.message!!.contains("overloaded_error"))
        }
    }

    @Test
    fun badKeyIsPermanentAndNeverLeaksTheKey() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""))

        try {
            interpreter.interpret("note", now)
            fail("Erreur attendue")
        } catch (e: InterpreterException) {
            assertFalse(e.isTransient)
            assertFalse(e.message!!.contains("sk-ant-test"))
        }
    }

    @Test
    fun answerWithoutFormIsAnError() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"content":[{"type":"text","text":"Bonjour"}]}"""))

        try {
            interpreter.interpret("note", now)
            fail("Erreur attendue")
        } catch (e: InterpreterException) {
            assertTrue(e.isTransient) // 502 : on réessaiera
        }
    }
}
