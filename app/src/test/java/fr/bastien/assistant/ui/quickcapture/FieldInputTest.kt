package fr.bastien.assistant.ui.quickcapture

import org.junit.Assert.assertEquals
import org.junit.Test

class FieldInputTest {

    @Test
    fun ordinaryTypingUpdatesTheText() {
        assertEquals(FieldInput.Update("Rappeler W"), FieldInput.interpret("Rappeler ", "Rappeler W"))
    }

    @Test
    fun enterAtTheEndSubmits() {
        assertEquals(FieldInput.Submit, FieldInput.interpret("Rappeler Weber", "Rappeler Weber\n"))
    }

    @Test
    fun enterInTheMiddleAlsoSubmits() {
        assertEquals(FieldInput.Submit, FieldInput.interpret("Rappeler Weber", "Rappeler\n Weber"))
    }

    @Test
    fun pastedLinesAreKeptWithSpaces() {
        assertEquals(
            FieldInput.Update("Ligne un ligne deux"),
            FieldInput.interpret("", "Ligne un\nligne deux"),
        )
    }
}
