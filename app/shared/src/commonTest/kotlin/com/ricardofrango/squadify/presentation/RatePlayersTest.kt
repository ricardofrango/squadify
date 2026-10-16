package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.model.TrainingRatingChange
import com.ricardofrango.squadify.presentation.ui.training.RatePlayersUiState
import com.ricardofrango.squadify.presentation.ui.training.RatingInput
import com.ricardofrango.squadify.presentation.ui.training.parseRating
import com.ricardofrango.squadify.presentation.ui.training.ratingChanges
import com.ricardofrango.squadify.presentation.ui.training.ratingText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Rate players: the rating box (0 to 10, one decimal, 0 = absent, empty = not rated) and what a save sends.
class RatePlayersTest {
    @Test
    fun aRatingIsZeroToTenWithUpToOneDecimal() {
        assertEquals(RatingInput.Valid(7.5), parseRating("7.5"))
        assertEquals(RatingInput.Valid(7.5), parseRating(" 7,5 "))
        assertEquals(RatingInput.Valid(10.0), parseRating("10"))
        assertEquals(RatingInput.Valid(10.0), parseRating("10.0"))
        // 0 means absent; empty means not rated.
        assertEquals(RatingInput.Valid(0.0), parseRating("0"))
        // Mid-typing "7." reads as 7, so the box doesn't flash red before the decimal.
        assertEquals(RatingInput.Valid(7.0), parseRating("7,"))
        assertEquals(RatingInput.Valid(null), parseRating(""))
        listOf("10.5", "11", "7.55", "-1", ".5", "abc").forEach { assertEquals(RatingInput.Invalid, parseRating(it), it) }
    }

    @Test
    fun aStoredRatingShowsWithoutATrailingZero() {
        assertEquals("8", ratingText(8.0))
        assertEquals("7.5", ratingText(7.5))
        assertEquals("0", ratingText(0.0))
        assertEquals("", ratingText(null))
    }

    @Test
    fun anInvalidBoxBlocksSavingAndKeepsTheLoadedValue() {
        val original = mapOf<String, Double?>("a" to 7.0, "b" to null)
        val state = RatePlayersUiState(loading = false, original = original, texts = mapOf("a" to "12", "b" to "6,5"))
        assertEquals(setOf("a"), state.invalid)
        assertEquals(mapOf<String, Double?>("a" to 7.0, "b" to 6.5), state.current)
        assertTrue(state.dirty)
        assertFalse(state.canSave)
        assertTrue(state.copy(texts = mapOf("a" to "0", "b" to "6,5")).canSave)
    }

    @Test
    fun aSaveSendsOnlyWhatChangedWithClearedAsNull() {
        val before = mapOf<String, Double?>("a" to 7.0, "b" to null, "c" to 6.0)
        val after = mapOf<String, Double?>("a" to 7.0, "b" to 0.0, "c" to null)
        assertEquals(listOf(TrainingRatingChange("b", 0.0), TrainingRatingChange("c", null)), ratingChanges(before, after))
    }
}
