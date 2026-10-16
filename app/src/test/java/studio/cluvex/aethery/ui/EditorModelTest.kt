package studio.cluvex.aethery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditorModelTest {
    @Test
    fun noValidatorMeansNoError() {
        assertNull(EditorModel.firstError(listOf("a", "b"), null))
    }

    @Test
    fun aValidatorsErrorIsReturnedWithTheFieldIndex() {
        val error = EditorModel.firstError(listOf("a", "b")) { 1 to "bad" }
        assertEquals(1 to "bad", error)
    }

    @Test
    fun aValidatorThatApprovesGivesNoError() {
        assertNull(EditorModel.firstError(listOf("a")) { null })
    }

    @Test
    fun theValidatorSeesTheExactValuesOnce() {
        var calls = 0
        var seen: List<String>? = null
        EditorModel.firstError(listOf("x", "", "z")) { values ->
            calls++
            seen = values
            null
        }
        assertEquals(1, calls)
        assertEquals(listOf("x", "", "z"), seen)
    }
}
