package com.ricardofrango.squadify.presentation.ui.livematch

import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.PlayerPosition
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class BenchOrderTest {
    private fun player(id: String, position: PlayerPosition?, shirt: Int?) =
        Player(id, "c1", id, "", LocalDate(2012, 1, 1), shirtNumber = shirt, active = true, position = position)

    // Goalkeeper to striker, by shirt number within a position; no position last.
    @Test
    fun theBenchGoesGoalkeeperFirstThenByPosition() {
        val bench = listOf(
            player("st9", PlayerPosition.STRIKER, 9),
            player("none", null, 2),
            player("df5", PlayerPosition.DEFENDER, 5),
            player("gk12", PlayerPosition.GOALKEEPER, 12),
            player("df3", PlayerPosition.DEFENDER, 3),
            player("mf8", PlayerPosition.MIDFIELD, 8),
            player("wg7", PlayerPosition.WINGER, 7)
        )
        assertEquals(listOf("gk12", "df3", "df5", "mf8", "wg7", "st9", "none"), bench.sortedByPosition().map { it.id })
    }
}
