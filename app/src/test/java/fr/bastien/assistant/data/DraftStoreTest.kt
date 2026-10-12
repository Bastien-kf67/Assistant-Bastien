package fr.bastien.assistant.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DraftStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DraftStore(context).clear()
    }

    @Test
    fun draftSurvivesANewInstance() {
        DraftStore(context).save("req-1", "Voir avec Julien")

        assertEquals(Draft("req-1", "Voir avec Julien"), DraftStore(context).load())
    }

    @Test
    fun clearRemovesTheDraft() {
        val store = DraftStore(context)
        store.save("req-1", "Voir avec Julien")
        store.clear()

        assertNull(DraftStore(context).load())
    }

    @Test
    fun blankDraftIsIgnored() {
        DraftStore(context).save("req-1", "   ")

        assertNull(DraftStore(context).load())
    }
}
