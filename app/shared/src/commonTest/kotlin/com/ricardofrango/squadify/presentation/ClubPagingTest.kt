package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.model.MatchListStatus
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.presentation.ui.clubdetails.MatchesCursor
import com.ricardofrango.squadify.presentation.ui.clubdetails.MatchesStatusFilter
import com.ricardofrango.squadify.presentation.ui.clubdetails.withActive
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The club's lists load a page at a time; the filters themselves run on the server.
class ClubPagingTest {

    @Test
    fun allWalksUpcomingThenTheNotPlayedThenTheResultsInTheOrderTheyHappen() {
        val cursor = MatchesCursor(MatchesStatusFilter.ALL)
        assertEquals(MatchListStatus.UPCOMING to 0, cursor.list to cursor.offset)

        cursor.advance(size = 30, more = true)
        assertEquals(MatchListStatus.UPCOMING to 30, cursor.list to cursor.offset)

        // The last upcoming page: the games never started follow, from their own first row.
        cursor.advance(size = 4, more = false)
        assertEquals(MatchListStatus.NOT_PLAYED to 0, cursor.list to cursor.offset)

        cursor.advance(size = 2, more = false)
        assertEquals(MatchListStatus.FINISHED to 0, cursor.list to cursor.offset)
        assertTrue(cursor.hasMore)

        cursor.advance(size = 12, more = false)
        assertFalse(cursor.hasMore)
    }

    @Test
    fun upcomingIsTheUpcomingThenTheNotPlayedAndFinishedIsOneList() {
        val upcoming = MatchesCursor(MatchesStatusFilter.UPCOMING)
        assertEquals(MatchListStatus.UPCOMING, upcoming.list)
        upcoming.advance(size = 3, more = false)
        assertEquals(MatchListStatus.NOT_PLAYED, upcoming.list)
        upcoming.advance(size = 0, more = false)
        assertFalse(upcoming.hasMore)

        val finished = MatchesCursor(MatchesStatusFilter.FINISHED)
        assertEquals(MatchListStatus.FINISHED, finished.list)
    }

    @Test
    fun disablingAPlayerKeepsTheirRowInPlace() {
        fun player(id: String) = Player(id = id, clubId = "c", firstName = id, lastName = "X", birthDate = LocalDate(2012, 1, 1), active = true)
        val list = listOf(player("p1"), player("p2"), player("p3"))

        val disabled = list.withActive("p2", active = false)
        assertEquals(listOf("p1", "p2", "p3"), disabled.map { it.id })
        assertFalse(disabled[1].active)
        assertTrue(disabled.withActive("p2", active = true)[1].active)
    }
}
