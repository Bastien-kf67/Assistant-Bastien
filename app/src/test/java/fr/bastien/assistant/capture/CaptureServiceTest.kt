package fr.bastien.assistant.capture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.bastien.assistant.data.TaskService
import fr.bastien.assistant.data.local.AssistantDatabase
import fr.bastien.assistant.domain.model.CaptureStatus
import fr.bastien.assistant.domain.model.SyncStatus
import fr.bastien.assistant.domain.model.TaskType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CaptureServiceTest {

    private lateinit var db: AssistantDatabase
    private lateinit var service: CaptureService
    private lateinit var tasks: TaskService
    private var now = 1_760_000_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AssistantDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        service = CaptureService(db, clock = { now })
        tasks = TaskService(db, clock = { now })
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun captureKeepsOriginalTextExactly() = runBlocking {
        val raw = "  Faudrait voir avec Fred pour Gerstheim   la semaine prochaine "
        val result = service.capture("req-1", raw, CaptureSource.ANDROID_TEXT)

        assertEquals(CaptureResult.Saved("req-1"), result)
        val stored = db.dao().getCapture("req-1")!!
        assertEquals(raw, stored.originalText)
        assertEquals(CaptureSource.ANDROID_TEXT, stored.source)
        assertEquals(CaptureStatus.CAPTURED, stored.status)
        assertEquals(now, stored.createdAt)
    }

    @Test
    fun captureCreatesOneTaskWithCleanTitleAndNoInventedData() = runBlocking {
        service.capture("req-1", "  Appeler   le garage ", CaptureSource.ANDROID_TEXT)

        val created = db.dao().tasksForCapture("req-1")
        assertEquals(1, created.size)
        val task = created.single()
        assertEquals("Appeler le garage", task.title)
        assertEquals(TaskType.TASK, task.type)
        assertNull(task.dueDate)
        assertFalse(task.done)
        assertEquals(SyncStatus.WAITING, task.syncStatus)
        assertNull(task.externalId)
    }

    @Test
    fun onSavedIsCalledOncePerNewCaptureOnly() = runBlocking {
        val sources = mutableListOf<CaptureSource>()
        val notifying = CaptureService(db, clock = { now }, onSaved = { sources += it })

        notifying.capture("req-1", "Rappeler Weber", CaptureSource.ANDROID_TEXT)
        notifying.capture("req-1", "Rappeler Weber", CaptureSource.ANDROID_TEXT)
        notifying.capture("req-2", "   ", CaptureSource.ANDROID_TEXT)

        assertEquals(listOf(CaptureSource.ANDROID_TEXT), sources)
    }

    @Test
    fun checkingASyncedTaskPutsItBackInTheQueueAndNotifies() = runBlocking {
        var changes = 0
        val notifyingTasks = TaskService(db, clock = { now }, onChanged = { changes++ })
        service.capture("req-1", "Rappeler Weber", CaptureSource.ANDROID_TEXT)
        val task = db.dao().tasksForCapture("req-1").single()
        db.dao().setSyncStatus(task.id, SyncStatus.SYNCED)

        notifyingTasks.setDone(task.id, true)
        notifyingTasks.setDone(task.id, true)

        assertEquals(SyncStatus.WAITING, db.dao().getTask(task.id)!!.syncStatus)
        assertEquals(1, changes)
    }

    @Test
    fun doubleSubmitWithSameRequestCreatesOnlyOneCapture() = runBlocking {
        val first = service.capture("req-1", "Rappeler Weber vendredi", CaptureSource.ANDROID_TEXT)
        val second = service.capture("req-1", "Rappeler Weber vendredi", CaptureSource.ANDROID_TEXT)

        assertEquals(CaptureResult.Saved("req-1"), first)
        assertEquals(CaptureResult.AlreadySaved("req-1"), second)
        assertEquals(1, db.dao().countCaptures())
        assertEquals(1, db.dao().tasksForCapture("req-1").size)
    }

    @Test
    fun simultaneousSubmitsCreateOnlyOneCapture() = runBlocking {
        val results = (1..5).map {
            async { service.capture("req-1", "Acheter filtres frigo", CaptureSource.ANDROID_TEXT) }
        }.awaitAll()

        assertEquals(1, results.count { it is CaptureResult.Saved })
        assertEquals(1, db.dao().countCaptures())
    }

    @Test
    fun staleRequestIdWithDifferentTextIsNeverLost() = runBlocking {
        service.capture("req-1", "Première chose", CaptureSource.ANDROID_TEXT)
        val result = service.capture("req-1", "Deuxième chose", CaptureSource.ANDROID_TEXT)

        assertTrue(result is CaptureResult.Saved)
        val newId = (result as CaptureResult.Saved).captureId
        assertNotEquals("req-1", newId)
        assertEquals(2, db.dao().countCaptures())
        assertEquals("Deuxième chose", db.dao().getCapture(newId)!!.originalText)
    }

    @Test
    fun blankTextIsNotSaved() = runBlocking {
        assertEquals(CaptureResult.Empty, service.capture("req-1", "   ", CaptureSource.ANDROID_TEXT))
        assertEquals(0, db.dao().countCaptures())
    }

    @Test
    fun voiceCaptureUsesSamePipeline() = runBlocking {
        service.capture("voice-1", "Commander les pièces", CaptureSource.ANDROID_VOICE)

        val stored = db.dao().getCapture("voice-1")!!
        assertEquals(CaptureSource.ANDROID_VOICE, stored.source)
        assertEquals(1, db.dao().tasksForCapture("voice-1").size)
    }

    @Test
    fun captureIsLogged() = runBlocking {
        service.capture("req-1", "Rappeler Weber", CaptureSource.ANDROID_TEXT)

        val log = db.dao().logForCapture("req-1")
        assertEquals(listOf("CAPTURE_RECEIVED"), log.map { it.action })
    }

    @Test
    fun checkingAndUncheckingATask() = runBlocking {
        service.capture("req-1", "Rappeler Weber", CaptureSource.ANDROID_TEXT)
        val task = db.dao().tasksForCapture("req-1").single()

        now += 60_000
        tasks.setDone(task.id, true)
        val done = db.dao().getTask(task.id)!!
        assertTrue(done.done)
        assertEquals(now, done.doneAt)

        tasks.setDone(task.id, false)
        val reopened = db.dao().getTask(task.id)!!
        assertFalse(reopened.done)
        assertNull(reopened.doneAt)

        val actions = db.dao().logForCapture("req-1").map { it.action }
        assertEquals(listOf("CAPTURE_RECEIVED", "TASK_DONE", "TASK_REOPENED"), actions)
    }
}
