package fr.bastien.assistant.domain.interpreter

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.bastien.assistant.capture.CaptureService
import fr.bastien.assistant.capture.CaptureSource
import fr.bastien.assistant.data.TaskService
import fr.bastien.assistant.data.local.AssistantDatabase
import fr.bastien.assistant.domain.model.CaptureStatus
import fr.bastien.assistant.domain.model.Category
import fr.bastien.assistant.domain.model.SyncStatus
import fr.bastien.assistant.domain.model.TaskType
import fr.bastien.assistant.integration.microsoft.FakeTodoApi
import fr.bastien.assistant.integration.microsoft.SyncCursor
import fr.bastien.assistant.integration.microsoft.TodoSyncEngine
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InterpretationEngineTest {

    /** Fausse IA : renvoie la réponse préparée, ou l'erreur préparée. */
    private class FakeInterpreter : TaskInterpreter {
        var answer: List<InterpretedTask> = emptyList()
        var error: InterpreterException? = null
        var calls = 0
        var lastNow: ZonedDateTime? = null

        override suspend fun interpret(text: String, now: ZonedDateTime): List<InterpretedTask> {
            calls++
            lastNow = now
            error?.let { throw it }
            return answer
        }
    }

    private lateinit var db: AssistantDatabase
    private lateinit var interpreter: FakeInterpreter
    private lateinit var engine: InterpretationEngine
    private var aiOn = true
    private var now = 1_760_536_800_000L // 15/10/2026 16:00 à Paris
    private var counter = 0

    private val captures by lazy { CaptureService(db, clock = { now }, holdForInterpretation = { aiOn && it != CaptureSource.TODO_IMPORT }) }
    private val tasks by lazy { TaskService(db, clock = { now }) }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AssistantDatabase::class.java).allowMainThreadQueries().build()
        interpreter = FakeInterpreter()
        engine = InterpretationEngine(db, interpreter, clock = { now }, zone = { ZoneId.of("Europe/Paris") })
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun task(
        title: String,
        type: TaskType = TaskType.TASK,
        category: Category = Category.PRO,
        due: String? = null,
        unclear: Boolean = false,
        question: String? = null,
    ) = InterpretedTask(title, type, category, due, 0.9, unclear, question)

    private suspend fun capture(text: String, source: CaptureSource = CaptureSource.ANDROID_VOICE): String {
        val id = "req-${++counter}"
        captures.capture(id, text, source)
        now += 1_000
        return id
    }

    @Test
    fun newCaptureIsHeldUntilInterpreted() = runBlocking {
        val id = capture("appeler weber et commander les filtres")

        val raw = db.dao().tasksForCapture(id).single()
        assertEquals(SyncStatus.HELD, raw.syncStatus)
        assertTrue(db.dao().tasksToPush().isEmpty())
    }

    @Test
    fun interpretationSplitsAndCleansWithoutTouchingOriginal() = runBlocking {
        val id = capture("appeler weber et commander les filtres vendredi")
        interpreter.answer = listOf(
            task("Appeler Weber"),
            task("Commander les filtres", due = "2026-10-16"),
        )

        assertEquals(1, engine.processPending())

        val result = db.dao().tasksForCapture(id).sortedBy { it.createdAt }
        assertEquals(listOf("Appeler Weber", "Commander les filtres"), result.map { it.title })
        assertEquals(listOf(null, "2026-10-16"), result.map { it.dueDate })
        assertTrue(result.all { it.syncStatus == SyncStatus.WAITING && it.category == Category.PRO })
        val capture = db.dao().getCapture(id)!!
        assertEquals("appeler weber et commander les filtres vendredi", capture.originalText)
        assertEquals(CaptureStatus.PROCESSED, capture.status)
        assertEquals(2, db.dao().tasksToPush().size)
        assertEquals("Europe/Paris", interpreter.lastNow!!.zone.id)
    }

    @Test
    fun ideasStayInAssistant() = runBlocking {
        val id = capture("augmenter les quantités de les maternelle dans l'application")
        interpreter.answer = listOf(task("Appli bébé : augmenter les quantités de lait maternel", TaskType.IDEA, Category.PROJETS))

        engine.processPending()

        val idea = db.dao().tasksForCapture(id).single()
        assertEquals(TaskType.IDEA, idea.type)
        assertEquals(SyncStatus.LOCAL_ONLY, idea.syncStatus)
        assertTrue(db.dao().tasksToPush().isEmpty())
    }

    @Test
    fun unclearCaptureIsMarkedButStillSent() = runBlocking {
        val id = capture("fais ça après le truc de demain")
        interpreter.answer = listOf(task("Faire ça après le truc de demain", category = Category.UNKNOWN, unclear = true, question = "De quelle tâche s'agit-il ?"))

        engine.processPending()

        val unclear = db.dao().tasksForCapture(id).single()
        assertTrue(unclear.needsClarification)
        assertEquals("De quelle tâche s'agit-il ?", unclear.clarificationQuestion)
        assertEquals(SyncStatus.WAITING, unclear.syncStatus)
        assertEquals(CaptureStatus.TO_CLARIFY, db.dao().getCapture(id)!!.status)
    }

    @Test
    fun userChangeBeforeAnalysisIsRespected() = runBlocking {
        val id = capture("apeler le garage")
        val raw = db.dao().tasksForCapture(id).single()
        tasks.rename(raw.id, "Appeler le garage Kia")
        assertEquals(SyncStatus.HELD, db.dao().getTask(raw.id)!!.syncStatus)
        interpreter.answer = listOf(task("Appeler le garage"))

        engine.processPending()

        val kept = db.dao().getTask(raw.id)!!
        assertEquals("Appeler le garage Kia", kept.title)
        assertEquals(SyncStatus.WAITING, kept.syncStatus)
        assertEquals(1, db.dao().tasksForCapture(id).size)
    }

    @Test
    fun permanentErrorSendsRawCapture() = runBlocking {
        val id = capture("rappeler Julien")
        interpreter.error = InterpreterException(401, "authentication_error : invalid x-api-key")

        engine.processPending()

        val raw = db.dao().tasksForCapture(id).single()
        assertEquals("rappeler Julien", raw.title)
        assertEquals(SyncStatus.WAITING, raw.syncStatus)
        val capture = db.dao().getCapture(id)!!
        assertEquals(CaptureStatus.ERROR, capture.status)
        assertTrue(capture.errorMessage!!.contains("authentication_error"))
    }

    @Test
    fun transientErrorKeepsCaptureHeldForRetry() = runBlocking {
        val id = capture("rappeler Julien")
        interpreter.error = InterpreterException(529, "overloaded")

        try {
            engine.processPending()
            fail("La panne passagère aurait dû remonter")
        } catch (e: InterpreterException) {
            assertTrue(e.isTransient)
        }

        assertEquals(SyncStatus.HELD, db.dao().tasksForCapture(id).single().syncStatus)
        assertEquals(CaptureStatus.CAPTURED, db.dao().getCapture(id)!!.status)
    }

    @Test
    fun givingUpReleasesEverything() = runBlocking {
        val first = capture("note un")
        val second = capture("note deux")

        engine.giveUp("IA injoignable")

        assertTrue(listOf(first, second).all { db.dao().tasksForCapture(it).single().syncStatus == SyncStatus.WAITING })
        assertEquals(CaptureStatus.ERROR, db.dao().getCapture(first)!!.status)
    }

    @Test
    fun capturesFromBeforeTheAiAreNotSentToIt() = runBlocking {
        aiOn = false
        val old = capture("ancienne capture", CaptureSource.ANDROID_TEXT)
        aiOn = true

        engine.processPending()

        assertEquals(0, interpreter.calls)
        assertEquals(CaptureStatus.PROCESSED, db.dao().getCapture(old)!!.status)
        assertEquals(SyncStatus.WAITING, db.dao().tasksForCapture(old).single().syncStatus)
    }

    @Test
    fun emptyAnswerKeepsRawCapture() = runBlocking {
        val id = capture("euh")
        interpreter.answer = emptyList()

        engine.processPending()

        assertEquals("euh", db.dao().tasksForCapture(id).single().title)
        assertEquals(SyncStatus.WAITING, db.dao().tasksForCapture(id).single().syncStatus)
        assertEquals(CaptureStatus.TO_CLARIFY, db.dao().getCapture(id)!!.status)
    }

    @Test
    fun checkingAHeldTaskDoesNotSendItRaw() = runBlocking {
        val id = capture("acheter du pain")
        val raw = db.dao().tasksForCapture(id).single()

        tasks.setDone(raw.id, true)

        assertEquals(SyncStatus.HELD, db.dao().getTask(raw.id)!!.syncStatus)
        assertTrue(db.dao().tasksToPush().isEmpty())
    }

    @Test
    fun interpretedTaskReachesTodoWithDateCategoryAndOriginalNote() = runBlocking {
        val api = FakeTodoApi()
        val cursor = object : SyncCursor {
            override var listId: String? = null
            override var deltaLink: String? = null
        }
        val sync = TodoSyncEngine(db, api, cursor, captures, listName = "Assistant (test)", clock = { now })
        capture("rappeler le garage vendredi")
        interpreter.answer = listOf(task("Rappeler le garage", category = Category.PERSONAL, due = "2026-10-16"))

        engine.processPending()
        sync.sync()

        val sent = api.tasks.values.single()
        assertEquals("Rappeler le garage", sent.title)
        assertEquals("2026-10-16", sent.dueDate)
        assertEquals(listOf("Perso"), sent.categories)
        assertEquals("Noté : « rappeler le garage vendredi »", sent.note)
    }

    @Test
    fun rawCaptureWithoutAiHasNoNoteNorCategory() = runBlocking {
        aiOn = false
        val api = FakeTodoApi()
        val cursor = object : SyncCursor {
            override var listId: String? = null
            override var deltaLink: String? = null
        }
        val sync = TodoSyncEngine(db, api, cursor, captures, listName = "Assistant (test)", clock = { now })
        capture("Rappeler le garage", CaptureSource.ANDROID_TEXT)

        sync.sync()

        val sent = api.tasks.values.single()
        assertNull(sent.note)
        assertTrue(sent.categories.isEmpty())
        assertFalse(sent.completed)
    }
}
