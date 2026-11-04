package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.model.PlayerSeasonMatch
import com.ricardofrango.squadify.model.PlayerTrainingSeason
import com.ricardofrango.squadify.model.PlayerTrainingSessionLine
import com.ricardofrango.squadify.presentation.ui.playerdetails.overallRating
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant
import com.ricardofrango.squadify.presentation.ui.playerdetails.PlayerTab
import com.ricardofrango.squadify.presentation.ui.playerdetails.forSeason
import com.ricardofrango.squadify.presentation.ui.playerdetails.playerTabs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerTabsTest {
    private fun season(id: String) = PlayerTrainingSeason(id, id, "Sub-13", sessions = 2, attended = 1, rated = 2, average = 7.0)

    @Test
    fun theTrainingTabOnlyShowsWhenThereIsTraining() {
        // The coach app, players and parents get no training seasons.
        assertEquals(listOf(PlayerTab.GENERAL, PlayerTab.GAMES), playerTabs(emptyList()))
        assertEquals(listOf(PlayerTab.GENERAL, PlayerTab.GAMES, PlayerTab.TRAINING), playerTabs(listOf(season("s1"))))
    }

    @Test
    fun theTrainingTabFollowsTheSeasonPickedInGames() {
        val training = listOf(season("s2"), season("s1"))
        assertEquals("s1", training.forSeason("s1")?.seasonId)
        // A season without training falls back to the latest one.
        assertEquals("s2", training.forSeason("s0")?.seasonId)
        assertNull(emptyList<PlayerTrainingSeason>().forSeason("s1"))
    }

    @Test
    fun theOverallRatingMixesGamesAndTrainingsWithAMissedTrainingAsZero() {
        val matches = listOf(7, null).mapIndexed { i, r ->
            PlayerSeasonMatch("m$i", Instant.fromEpochMilliseconds(0), "Rival", null, true, 60, 0, 0, 0, 0, 0, rating = r)
        }
        val training = season("s1").copy(sessionLines = listOf(
            PlayerTrainingSessionLine("t1", LocalDate(2026, 10, 1), LocalTime(18, 0), 8.0),
            PlayerTrainingSessionLine("t2", LocalDate(2026, 10, 2), LocalTime(18, 0), 0.0),
            PlayerTrainingSessionLine("t3", LocalDate(2026, 10, 3), LocalTime(18, 0), null)
        ))
        // (7 + 8 + 0) / 3: the unrated game and the unrated session are left out.
        assertEquals(5.0, overallRating(matches, training))
        // Missed trainings left out instead: (7 + 8) / 2.
        assertEquals(7.5, overallRating(matches, training, missedAsZero = false))
        assertEquals(7.0, overallRating(matches, null))
        assertNull(overallRating(emptyList(), null))
    }
}
