package com.ricardofrango.squadify.presentation.ui.matchreport

import com.ricardofrango.squadify.model.MatchPlayerStats
import com.ricardofrango.squadify.model.MatchPlayerStatsWithPlayer
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.PlayerPosition
import com.ricardofrango.squadify.presentation.ui.matchdetails.CallUpSort
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MatchReportLogicTest {

    private fun row(id: String, number: Int?, first: String = "F$id", position: PlayerPosition? = null) = MatchPlayerStatsWithPlayer(
        MatchPlayerStats("m", id, true, 70, 0, 0, 0, 0, 0),
        Player(id, "c", first, "L$id", LocalDate(2011, 1, 1), shirtNumber = number, active = true, position = position)
    )

    @Test
    fun ratingsFallIntoFourBands() {
        assertEquals(listOf(RatingBand.LOW, RatingBand.LOW, RatingBand.MID, RatingBand.MID, RatingBand.GOOD, RatingBand.GOOD, RatingBand.TOP, RatingBand.TOP),
            listOf(0, 4, 5, 6, 7, 8, 9, 10).map(::ratingBand))
    }

    @Test
    fun theTeamSheetListsPlayersByShirtNumberWithUnnumberedLast() {
        val ordered = teamSheetOrder(listOf(row("c", null), row("a", 10), row("b", 1)))
        assertEquals(listOf("b", "a", "c"), ordered.map { it.player.id })
    }

    private val squad = listOf(
        row("a", 9, "Tomás", PlayerPosition.STRIKER),
        row("b", 1, "Rui", PlayerPosition.GOALKEEPER),
        row("c", 14, "Bruno", null),
        row("d", 4, "Gil", PlayerPosition.DEFENDER)
    )

    @Test
    fun theRatingsTabOpensSortedByName() {
        assertEquals(listOf("Bruno", "Gil", "Rui", "Tomás"), ratingOrder(squad).map { it.player.firstName })
    }

    @Test
    fun theRatingsTabSortsByPositionWithNoPositionLast() {
        assertEquals(listOf("Rui", "Gil", "Tomás", "Bruno"), ratingOrder(squad, CallUpSort.POSITION).map { it.player.firstName })
    }

    @Test
    fun nextUnratedMovesForwardAndWrapsAround() {
        val ids = listOf("a", "b", "c", "d")
        val ratings = mapOf("a" to 7, "b" to null, "c" to 6, "d" to null)
        assertEquals("d", nextUnrated(ids, ratings, current = "c"))
        assertEquals("b", nextUnrated(ids, ratings, current = "d"))
        assertEquals("b", nextUnrated(ids, ratings, current = null))
    }

    @Test
    fun nextUnratedIsNullWhenEveryoneElseIsRated() {
        val ids = listOf("a", "b")
        assertNull(nextUnrated(ids, mapOf("a" to 7, "b" to null), current = "b"))
        assertNull(nextUnrated(ids, mapOf("a" to 7, "b" to 8), current = "a"))
    }

    @Test
    fun readingByRatingPutsTheHighestFirstTiesByNumberAndUnratedLast() {
        val rows = listOf(row("a", 4), row("b", 2), row("c", 9), row("d", 1))
        val ratings = mapOf("a" to 7, "b" to 7, "c" to null, "d" to 9)
        assertEquals(listOf("d", "b", "a", "c"), readOrder(rows, ratings, ReportPlayerSort.RATING).map { it.player.id })
        assertEquals(listOf("d", "b", "a", "c"), readOrder(rows, ratings, ReportPlayerSort.NUMBER).map { it.player.id })
    }

    @Test
    fun theSummaryAveragesOnlyRatedPlayers() {
        assertEquals(RatingSummary(3, 5, "7.3"), ratingSummary(listOf(7, null, 8, 7, null)))
        assertEquals(RatingSummary(0, 2, null), ratingSummary(listOf(null, null)))
        assertEquals(RatingSummary(1, 1, "10.0"), ratingSummary(listOf(10)))
    }
}
