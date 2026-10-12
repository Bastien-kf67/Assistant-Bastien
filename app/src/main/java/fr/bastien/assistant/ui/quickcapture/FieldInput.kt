package fr.bastien.assistant.ui.quickcapture

/**
 * Règle du champ de saisie : la touche Entrée valide, elle ne crée jamais de retour à la ligne.
 *
 * Un seul retour à la ligne ajouté au texte précédent = appui sur Entrée.
 * Un texte collé contenant des retours à la ligne est gardé, avec des espaces à leur place.
 */
sealed interface FieldInput {
    data object Submit : FieldInput
    data class Update(val text: String) : FieldInput

    companion object {
        fun interpret(previous: String, next: String): FieldInput {
            if ('\n' !in next) return Update(next)
            val isSingleEnter = next.length == previous.length + 1 && next.replace("\n", "") == previous
            return if (isSingleEnter) Submit else Update(next.replace('\n', ' '))
        }
    }
}
