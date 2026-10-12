package fr.bastien.assistant.ui.main

import fr.bastien.assistant.data.local.TaskEntity
import java.text.Normalizer
import java.util.Locale

/** Recherche simple : sans accents ni majuscules, tous les mots tapés doivent apparaître. */
object TaskSearch {

    private val marks = Regex("\\p{M}+")

    fun normalize(text: String): String =
        marks.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(Locale.FRENCH)

    fun filter(tasks: List<TaskEntity>, query: String): List<TaskEntity> {
        val words = normalize(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return tasks
        return tasks.filter { task ->
            val title = normalize(task.title)
            words.all { it in title }
        }
    }
}
