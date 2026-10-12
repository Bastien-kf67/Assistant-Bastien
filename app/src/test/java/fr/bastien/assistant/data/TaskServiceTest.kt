package fr.bastien.assistant.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.bastien.assistant.capture.CaptureService
import fr.bastien.assistant.capture.CaptureSource
import fr.bastien.assistant.data.local.AssistantDatabase
import fr.bastien.assistant.data.local.TaskEntity
import fr.bastien.assistant.domain.model.SyncStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskServiceTest {

    private lateinit var db: AssistantDatabase
    private lateinit var captures: CaptureService
    private lateinit var service: TaskService
    private var now = 1_760_000_000_000L
    private var changes = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AssistantDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        captures = CaptureService(db, clock = { now })
        service = TaskService(db, clock = { now }, onChanged = { changes++ })
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun task(text: String = "Apeler Weber"): TaskEntity {
        captures.capture("req-$text", text, CaptureSource.ANDROID_TEXT)
        now += 1_000
        return db.dao().tasksForCapture("req-$text").single()
    }

    @Test
    fun renameChangesTitleButNeverOriginalText() = runBlocking {
        val task = task()
        db.dao().setSyncStatus(task.id, SyncStatus.SYNCED)

        assertTrue(service.rename(task.id, "  Appeler   Weber "))

        val renamed = db.dao().getTask(task.id)!!
        assertEquals("Appeler Weber", renamed.title)
        assertEquals(SyncStatus.WAITING, renamed.syncStatus)
        assertEquals("Apeler Weber", db.dao().getCapture(task.captureId)!!.originalText)
        assertEquals(1, changes)
    }

    @Test
    fun blankOrIdenticalRenameIsIgnored() = runBlocking {
        val task = task()

        assertFalse(service.rename(task.id, "   "))
        assertFalse(service.rename(task.id, "Apeler Weber"))

        assertEquals("Apeler Weber", db.dao().getTask(task.id)!!.title)
        assertEquals(0, changes)
    }

    @Test
    fun trashHidesFromHistoryAndRestoreBringsBack() = runBlocking {
        val task = task()

        service.moveToTrash(task.id)
        assertTrue(db.dao().observeHistory().first().isEmpty())
        assertEquals(listOf(task.id), db.dao().observeTrash(0).first().map { it.id })
        assertNotNull(db.dao().getTask(task.id)!!.deletedAt)

        service.restore(task.id)
        assertEquals(listOf(task.id), db.dao().observeHistory().first().map { it.id })
        assertTrue(db.dao().observeTrash(0).first().isEmpty())
        assertNull(db.dao().getTask(task.id)!!.deletedAt)
    }

    @Test
    fun trashOnlyShowsTheLastThirtyDays() = runBlocking {
        val old = task("Vieille tâche")
        service.moveToTrash(old.id)
        val deletedAt = db.dao().getTask(old.id)!!.deletedAt!!

        assertEquals(1, db.dao().observeTrash(deletedAt).first().size)
        assertTrue(db.dao().observeTrash(deletedAt + 1).first().isEmpty())
    }

    @Test
    fun neverSentTaskTrashedIsNotPushed() = runBlocking {
        val task = task()

        service.moveToTrash(task.id)

        assertTrue(db.dao().tasksToPush().none { it.id == task.id })
    }

    @Test
    fun actionsAreLogged() = runBlocking {
        val task = task()
        service.rename(task.id, "Appeler Weber")
        service.moveToTrash(task.id)
        service.restore(task.id)

        val actions = db.dao().logForCapture(task.captureId).map { it.action }
        assertEquals(listOf("CAPTURE_RECEIVED", "TASK_RENAMED", "TASK_TRASHED", "TASK_RESTORED"), actions)
    }
}
