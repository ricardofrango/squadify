package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.model.AgeGroupTrainingStats
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.TrainingRating
import com.ricardofrango.squadify.model.TrainingSession
import com.ricardofrango.squadify.presentation.ui.training.TrainingSort
import com.ricardofrango.squadify.presentation.ui.training.trainingMonths
import com.ricardofrango.squadify.presentation.ui.training.trainingTable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Training stats (design 39j): attendance is rated above 0 out of the period's sessions; Absent isn't averaged.
class TrainingStatsTest {
    private val tomas = Player("p1", "c", "Tomás", "Bragança", LocalDate(2012, 1, 1), shirtNumber = 10, active = true)
    private val caetano = Player("p2", "c", "Caetano", "Lousada", LocalDate(2012, 1, 1), shirtNumber = 14, active = true)
    private fun session(id: String, date: LocalDate) = TrainingSession(id, "ag", date, LocalTime(18, 0), LocalTime(19, 0))
    private val stats = AgeGroupTrainingStats(
        players = listOf(caetano, tomas),
        sessions = listOf(session("s1", LocalDate(2026, 9, 29)), session("s2", LocalDate(2026, 10, 6)), session("s3", LocalDate(2026, 10, 13))),
        ratings = mapOf(
            "s1" to listOf(TrainingRating("p1", 8.0), TrainingRating("p2", 6.0)),
            "s2" to listOf(TrainingRating("p1", 9.0), TrainingRating("p2", 0.0)),
            "s3" to listOf(TrainingRating("p1", 7.0))
        )
    )

    @Test
    fun theSeasonCountsEverySessionAndLeavesAbsentOutOfTheAverage() {
        val rows = trainingTable(stats, null, TrainingSort.ATTENDANCE)
        assertEquals(listOf("p1", "p2"), rows.map { it.player.id })
        assertEquals(3, rows[0].attended)
        assertEquals(8.0, rows[0].average)
        // Caetano: rated in two, Absent in one, unrated in another -> 1 of 3, average 6.
        assertEquals(1, rows[1].attended)
        assertEquals(33, rows[1].percent)
        assertEquals(6.0, rows[1].average)
    }

    @Test
    fun aMonthCountsOnlyItsSessions() {
        val october = trainingTable(stats, LocalDate(2026, 10, 1), TrainingSort.NAME)
        assertEquals(listOf("p2", "p1"), october.map { it.player.id })
        assertEquals(2, october[0].sessions)
        assertEquals(0, october[0].attended)
        assertNull(october[0].average)
        assertEquals(listOf(LocalDate(2026, 10, 1), LocalDate(2026, 9, 1)), trainingMonths(stats))
    }
}
