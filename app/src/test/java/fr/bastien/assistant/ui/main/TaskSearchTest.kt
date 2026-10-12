package fr.bastien.assistant.ui.main

import fr.bastien.assistant.data.local.TaskEntity
import fr.bastien.assistant.domain.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskSearchTest {

    private fun task(title: String) = TaskEntity(
        id = title,
        captureId = title,
        type = TaskType.TASK,
        title = title,
        createdAt = 0,
        updatedAt = 0,
    )

    private val tasks = listOf(
        task("Rappeler Weber vendredi"),
        task("Prévoir l'élagage à Gerstheim"),
        task("Commander les filtres"),
    )

    @Test
    fun ignoresAccentsAndCase() {
        assertEquals(listOf("Prévoir l'élagage à Gerstheim"), TaskSearch.filter(tasks, "ELAGAGE").map { it.title })
    }

    @Test
    fun allWordsMustMatchInAnyOrder() {
        assertEquals(listOf("Rappeler Weber vendredi"), TaskSearch.filter(tasks, "vendredi weber").map { it.title })
        assertEquals(emptyList<String>(), TaskSearch.filter(tasks, "weber filtres").map { it.title })
    }

    @Test
    fun emptyQueryShowsEverything() {
        assertEquals(3, TaskSearch.filter(tasks, "  ").size)
    }
}
