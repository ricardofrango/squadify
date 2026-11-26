package com.sportssoftw.squadcall.presentation

import com.sportssoftw.squadcall.model.Player
import com.sportssoftw.squadcall.model.PlayerPosition
import com.sportssoftw.squadcall.presentation.ui.matchdetails.CallUpSort
import com.sportssoftw.squadcall.presentation.ui.matchdetails.playerComparator
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

// The call-up screen's Position / Name chips -- the same order for the call-up and the Available pool.
class CallUpSortTest {
    private fun player(first: String, last: String, position: PlayerPosition?) =
        Player(id = first, clubId = "c", firstName = first, lastName = last, birthDate = LocalDate(2011, 1, 1), active = true, position = position)

    private val players = listOf(
        player("Tomás", "Bragança", PlayerPosition.STRIKER),
        player("Rui", "Perestrelo", PlayerPosition.GOALKEEPER),
        player("Bruno", "Tavares", null),
        player("Gil", "Bettencourt", PlayerPosition.DEFENDER),
        player("Duarte", "Vilaça", PlayerPosition.MIDFIELD)
    )

    @Test
    fun byPositionGoesGoalkeeperToStrikerWithNoPositionLast() {
        assertEquals(listOf("Rui", "Gil", "Duarte", "Tomás", "Bruno"), players.sortedWith(playerComparator(CallUpSort.POSITION)).map { it.firstName })
    }

    @Test
    fun byNameFollowsTheNameAsShownFirstNameFirst() {
        assertEquals(listOf("Bruno", "Duarte", "Gil", "Rui", "Tomás"), players.sortedWith(playerComparator(CallUpSort.NAME)).map { it.firstName })
    }
}
