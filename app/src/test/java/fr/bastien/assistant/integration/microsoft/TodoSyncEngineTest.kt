package fr.bastien.assistant.integration.microsoft

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.bastien.assistant.capture.CaptureService
import fr.bastien.assistant.capture.CaptureSource
import fr.bastien.assistant.data.TaskService
import fr.bastien.assistant.data.local.AssistantDatabase
import fr.bastien.assistant.data.local.TaskEntity
import fr.bastien.assistant.domain.model.SyncStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TodoSyncEngineTest {

    private class MemoryCursor : SyncCursor {
        override var listId: String? = null
        override var deltaLink: String? = null
    }

    private lateinit var db: AssistantDatabase
    private lateinit var captures: CaptureService
    private lateinit var tasks: TaskService
    private lateinit var api: FakeTodoApi
    private lateinit var cursor: MemoryCursor
    private lateinit var engine: TodoSyncEngine
    private var now = 1_760_000_000_000L
    private var counter = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AssistantDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        captures = CaptureService(db, clock = { now })
        tasks = TaskService(db, clock = { now })
        api = FakeTodoApi()
        cursor = MemoryCursor()
        engine = TodoSyncEngine(db, api, cursor, captures, listName = "Assistant (test)", clock = { now })
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun capture(text: String): TaskEntity {
        val id = "req-${++counter}"
        captures.capture(id, text, CaptureSource.ANDROID_TEXT)
        now += 1_000
        return db.dao().tasksForCapture(id).single()
    }

    private suspend fun reload(task: TaskEntity): TaskEntity = db.dao().getTask(task.id)!!

    @Test
    fun newCaptureIsSentOnceToTheTestList() = runBlocking {
        val task = capture("Rappeler Weber vendredi")

        engine.sync()
        engine.sync()

        val synced = reload(task)
        assertEquals(1, api.createCalls)
        assertEquals(1, api.listCreations)
        assertEquals("Assistant (test)", api.lists.values.single())
        assertEquals(SyncStatus.SYNCED, synced.syncStatus)
        assertEquals("Rappeler Weber vendredi", api.tasks.getValue(synced.externalId!!).title)
    }

    @Test
    fun existingListIsReused() = runBlocking {
        api.findOrCreateList("Assistant (test)")
        capture("Commander les filtres")

        engine.sync()

        assertEquals(1, api.lists.size)
    }

    @Test
    fun taskCheckedInTodoIsCheckedInAssistant() = runBlocking {
        val task = capture("Acheter filtres frigo")
        engine.sync()

        api.completeRemote(reload(task).externalId!!)
        engine.sync()

        val updated = reload(task)
        assertTrue(updated.done)
        assertNotNull(updated.doneAt)
        assertEquals(SyncStatus.SYNCED, updated.syncStatus)
    }

    @Test
    fun taskCheckedInAssistantIsCheckedInTodo() = runBlocking {
        val task = capture("Voir avec Julien")
        engine.sync()

        tasks.setDone(task.id, true)
        engine.sync()

        val synced = reload(task)
        assertTrue(api.tasks.getValue(synced.externalId!!).completed)
        assertEquals(SyncStatus.SYNCED, synced.syncStatus)
    }

    @Test
    fun pendingLocalChangeWinsOverTodo() = runBlocking {
        val task = capture("Appeler le garage")
        engine.sync()
        val externalId = reload(task).externalId!!

        // Coché dans Assistant, renommé dans To Do avant que l'envoi parte.
        tasks.setDone(task.id, true)
        api.renameRemote(externalId, "Appeler le garage Renault")
        engine.sync()

        assertTrue(reload(task).done)
        assertTrue(api.tasks.getValue(externalId).completed)
    }

    @Test
    fun titleChangedInTodoUpdatesTaskButNeverOriginalText() = runBlocking {
        val task = capture("Appeler le garage")
        engine.sync()

        api.renameRemote(reload(task).externalId!!, "Appeler le garage Renault")
        engine.sync()

        assertEquals("Appeler le garage Renault", reload(task).title)
        assertEquals("Appeler le garage", db.dao().getCapture(task.captureId)!!.originalText)
    }

    @Test
    fun taskDeletedInTodoLeavesHistoryButStaysInDatabase() = runBlocking {
        val task = capture("Prévoir ça semaine prochaine")
        engine.sync()

        api.deleteRemote(reload(task).externalId!!)
        engine.sync()

        val deleted = reload(task)
        assertNotNull(deleted.deletedAt)
        assertNull(deleted.externalId)
        assertNotNull(db.dao().getCapture(task.captureId))
    }

    @Test
    fun renamedInAssistantIsRenamedInTodo() = runBlocking {
        val task = capture("Apeler Weber")
        engine.sync()

        tasks.rename(task.id, "Appeler Weber")
        engine.sync()

        val synced = reload(task)
        assertEquals("Appeler Weber", api.tasks.getValue(synced.externalId!!).title)
        assertEquals(SyncStatus.SYNCED, synced.syncStatus)
        assertEquals("Apeler Weber", db.dao().getCapture(task.captureId)!!.originalText)
    }

    @Test
    fun trashedInAssistantIsDeletedInTodo() = runBlocking {
        val task = capture("Tâche en trop")
        engine.sync()
        val externalId = reload(task).externalId!!

        tasks.moveToTrash(task.id)
        engine.sync()
        engine.sync()

        assertFalse(externalId in api.tasks)
        val trashed = reload(task)
        assertNotNull(trashed.deletedAt)
        assertNull(trashed.externalId)
        assertEquals(SyncStatus.SYNCED, trashed.syncStatus)
    }

    @Test
    fun restoredAfterDeletionIsSentAgain() = runBlocking {
        val task = capture("Rappeler Weber")
        engine.sync()
        tasks.moveToTrash(task.id)
        engine.sync()

        tasks.restore(task.id)
        engine.sync()

        val restored = reload(task)
        assertNull(restored.deletedAt)
        assertEquals(SyncStatus.SYNCED, restored.syncStatus)
        assertEquals(listOf("Rappeler Weber"), api.tasks.values.map { it.title })
        assertEquals(2, api.createCalls)
    }

    @Test
    fun restoredBeforeDeletionLeftCreatesNoDuplicate() = runBlocking {
        val task = capture("Rappeler Weber")
        engine.sync()

        tasks.moveToTrash(task.id)
        tasks.restore(task.id)
        engine.sync()

        assertEquals(0, api.deleteCalls)
        assertEquals(1, api.createCalls)
        assertEquals(1, api.tasks.size)
        assertEquals(SyncStatus.SYNCED, reload(task).syncStatus)
    }

    @Test
    fun taskVanishedFromTodoIsRecreatedWhenChangedHere() = runBlocking {
        val task = capture("Commander les filtres")
        engine.sync()
        // Disparue de To Do sans que le changement soit encore arrivé ici.
        api.tasks.remove(reload(task).externalId!!)

        tasks.setDone(task.id, true)
        engine.sync()

        val synced = reload(task)
        assertEquals(SyncStatus.SYNCED, synced.syncStatus)
        assertTrue(api.tasks.getValue(synced.externalId!!).completed)
        assertEquals(2, api.createCalls)
    }

    @Test
    fun taskCreatedInTodoIsImportedAndNotSentBack() = runBlocking {
        engine.sync()
        val remoteId = api.addRemote("Rappeler le comptable", completed = true)

        engine.sync()
        engine.sync()

        val imported = db.dao().findByExternalId(remoteId)!!
        assertEquals("Rappeler le comptable", imported.title)
        assertTrue(imported.done)
        assertEquals(SyncStatus.SYNCED, imported.syncStatus)
        assertEquals(CaptureSource.TODO_IMPORT, db.dao().getCapture(imported.captureId)!!.source)
        assertEquals(0, api.createCalls)
        assertEquals(1, api.tasks.size)
    }

    @Test
    fun lostLinkIsRecoveredWithoutDuplicate() = runBlocking {
        engine.sync()
        val task = capture("Commander les pièces")
        // L'envoi a eu lieu mais l'appli a été coupée avant de noter le lien.
        api.addRemote("Commander les pièces")

        engine.sync()

        assertEquals(1, api.tasks.size)
        assertEquals(0, api.createCalls)
        assertEquals(SyncStatus.SYNCED, reload(task).syncStatus)
        assertNotNull(reload(task).externalId)
    }

    @Test
    fun transientErrorKeepsTaskWaitingThenSendsOnce() = runBlocking {
        val task = capture("Rappeler Weber")
        api.failNextCreate = TodoApiException(503, "indisponible")

        try {
            engine.sync()
            fail("La panne aurait dû remonter")
        } catch (e: TodoApiException) {
            assertTrue(e.isTransient)
        }
        assertEquals(SyncStatus.WAITING, reload(task).syncStatus)
        assertNull(reload(task).externalId)

        engine.sync()

        assertEquals(1, api.createCalls)
        assertEquals(SyncStatus.SYNCED, reload(task).syncStatus)
    }

    @Test
    fun rejectedTaskIsMarkedInErrorAndOthersStillGo() = runBlocking {
        val rejected = capture("Tâche refusée")
        val other = capture("Tâche normale")
        api.failNextCreate = TodoApiException(400, "invalid")

        engine.sync()

        assertEquals(SyncStatus.ERROR, reload(rejected).syncStatus)
        assertEquals(SyncStatus.SYNCED, reload(other).syncStatus)
        assertEquals(1, api.createCalls)
    }

    @Test
    fun expiredDeltaLinkRestartsFromFullList() = runBlocking {
        val task = capture("Voir avec Fred")
        engine.sync()
        api.completeRemote(reload(task).externalId!!)
        api.failNextDeltaWithLink = TodoApiException(410, "sync state expired")

        engine.sync()

        assertTrue(reload(task).done)
    }

    @Test
    fun deletedListIsRecreatedAndTasksSentAgain() = runBlocking {
        val task = capture("Rappeler Weber")
        engine.sync()
        api.deleteList(cursor.listId!!)

        engine.sync()

        assertEquals(2, api.listCreations)
        assertEquals(1, api.tasks.size)
        assertEquals(SyncStatus.SYNCED, reload(task).syncStatus)
        assertEquals("Rappeler Weber", api.tasks.values.single().title)
    }

    @Test
    fun capturesFromBeforeConnectionAreQueued() = runBlocking {
        val task = capture("Capture du Jalon 1")
        db.dao().setSyncStatus(task.id, SyncStatus.LOCAL_ONLY)

        assertEquals(1, db.dao().queueUnsentTasks())
        engine.sync()

        assertEquals(SyncStatus.SYNCED, reload(task).syncStatus)
        assertFalse(api.tasks.isEmpty())
    }

    @Test
    fun syncIsLogged() = runBlocking {
        val task = capture("Rappeler Weber")
        engine.sync()

        val actions = db.dao().logForCapture(task.captureId).map { it.action }
        assertEquals(listOf("CAPTURE_RECEIVED", "TODO_SENT"), actions)
    }
}
