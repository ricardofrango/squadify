package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.PlayerPosition
import com.ricardofrango.squadify.presentation.ui.matchdetails.CallUpSort
import com.ricardofrango.squadify.presentation.ui.matchrecord.minutesOrder
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

// The match record's minutes list: by name (the default) or by position, as Rate players.
class MatchRecordMinutesOrderTest {
    private fun player(first: String, shirt: Int, position: PlayerPosition?) =
        Player(id = first, clubId = "c", firstName = first, lastName = "X", birthDate = LocalDate(2011, 1, 1), shirtNumber = shirt, active = true, position = position)

    private val players = listOf(
        player("Tomás", 9, PlayerPosition.STRIKER),
        player("Rui", 1, PlayerPosition.GOALKEEPER),
        player("Bruno", 14, null),
        player("Gil", 4, PlayerPosition.DEFENDER)
    )

    @Test
    fun opensSortedByNameNotShirtNumber() {
        assertEquals(listOf("Bruno", "Gil", "Rui", "Tomás"), minutesOrder(players).map { it.firstName })
    }

    @Test
    fun byPositionGoesGoalkeeperToStrikerWithNoPositionLast() {
        assertEquals(listOf("Rui", "Gil", "Tomás", "Bruno"), minutesOrder(players, CallUpSort.POSITION).map { it.firstName })
    }
}
