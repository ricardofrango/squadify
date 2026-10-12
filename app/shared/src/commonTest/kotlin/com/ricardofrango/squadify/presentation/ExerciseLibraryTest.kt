package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.model.TrainingExercise
import com.ricardofrango.squadify.presentation.ui.training.ExerciseForm
import com.ricardofrango.squadify.presentation.ui.training.filterLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The exercise library (design 39h): search, what's already in the session, and the form.
class ExerciseLibraryTest {
    private val rondo = TrainingExercise("e1", "ag", "Rondo 4v1", "Possession in a 10×10 square")
    private val sprints = TrainingExercise("e2", "ag", "Box to box sprints", "Recovery runs")

    @Test
    fun searchMatchesNameOrDescriptionAndHidesTheSessionsOwn() {
        assertEquals(listOf(sprints), filterLibrary(listOf(rondo, sprints), "recovery", emptySet()))
        assertEquals(listOf(rondo), filterLibrary(listOf(rondo, sprints), "RONDO", emptySet()))
        assertEquals(listOf(sprints), filterLibrary(listOf(rondo, sprints), "", setOf("e1")))
    }

    @Test
    fun anExerciseNeedsANameAndAPositiveDuration() {
        assertFalse(ExerciseForm(name = " ").valid)
        assertFalse(ExerciseForm(name = "Rondo", duration = "0").valid)
        assertTrue(ExerciseForm(name = "Rondo", duration = "").valid)
        assertTrue(ExerciseForm(name = "Rondo", duration = "15").valid)
    }

    @Test
    fun theRequestTrimsAndLeavesEmptyFieldsOut() {
        val request = ExerciseForm(name = " Rondo ", description = " ", duration = "15", objectives = "Body shape").toRequest()
        assertEquals("Rondo", request.name)
        assertNull(request.description)
        assertEquals(15, request.durationMinutes)
        assertEquals("Body shape", request.objectives)
    }
}
