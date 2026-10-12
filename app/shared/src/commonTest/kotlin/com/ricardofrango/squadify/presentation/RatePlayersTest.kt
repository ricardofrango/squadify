package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.model.TrainingRatingChange
import com.ricardofrango.squadify.presentation.ui.training.nextUnrated
import com.ricardofrango.squadify.presentation.ui.training.ratingChanges
import com.ricardofrango.squadify.presentation.ui.training.stepRating
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Rate players (design 39g): − / + steps, the next unrated player, and what a save sends.
class RatePlayersTest {
    @Test
    fun stepsAreTenthsKeptBetweenPointOneAndTen() {
        assertEquals(7.1, stepRating(7.0, 1))
        assertEquals(10.0, stepRating(10.0, 1))
        // Stepping never makes a player Absent.
        assertEquals(0.1, stepRating(0.1, -1))
        // Unrated starts from 6.
        assertEquals(6.1, stepRating(null, 1))
    }

    @Test
    fun theNextUnratedPlayerWrapsAround() {
        val ids = listOf("a", "b", "c", "d")
        val ratings = mapOf("a" to null, "b" to 7.0, "c" to null, "d" to 8.0)
        assertEquals("c", nextUnrated(ids, ratings, "a"))
        assertEquals("a", nextUnrated(ids, ratings, "c"))
        assertNull(nextUnrated(ids, ids.associateWith { 7.0 }, "a"))
    }

    @Test
    fun aSaveSendsOnlyWhatChangedWithClearedAsNull() {
        val before = mapOf<String, Double?>("a" to 7.0, "b" to null, "c" to 6.0)
        val after = mapOf<String, Double?>("a" to 7.0, "b" to 0.0, "c" to null)
        assertEquals(listOf(TrainingRatingChange("b", 0.0), TrainingRatingChange("c", null)), ratingChanges(before, after))
    }
}
