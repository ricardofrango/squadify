package com.ricardofrango.squadify.presentation.ui.fullstats

import com.ricardofrango.squadify.model.FullStatsColumn
import com.ricardofrango.squadify.model.FullStatsRow
import com.ricardofrango.squadify.model.PlayerPosition
import com.ricardofrango.squadify.model.TeamSeasonPlayer
import com.ricardofrango.squadify.model.sortedBy
import com.ricardofrango.squadify.presentation.ui.components.twoDecimals
import kotlin.test.Test
import kotlin.test.assertEquals

class FullStatsSortTest {
    private fun row(name: String, position: PlayerPosition?) = FullStatsRow(
        TeamSeasonPlayer(name, name, position = position), games = 1, goals = 0, assists = 0, minutes = 0,
        gamesRating = null, trainingRating = null, finalRating = null, ratings = 0, won = 0, drawn = 0, lost = 0, yellowCards = 0, redCards = 0
    )

    // The position on the pitch, in PlayerPosition's order (not alphabetical); no position always last.
    @Test
    fun thePositionSortsGoalkeeperToStrikerEitherWay() {
        val rows = listOf(
            row("Ana", PlayerPosition.STRIKER), row("Bia", null), row("Caro", PlayerPosition.GOALKEEPER),
            row("Duda", PlayerPosition.MIDFIELD), row("Eva", PlayerPosition.DEFENDER), row("Filipa", PlayerPosition.WINGER)
        )
        assertEquals(listOf("Caro", "Eva", "Duda", "Filipa", "Ana", "Bia"), rows.sortedBy(FullStatsColumn.POSITION, descending = false).map { it.player.name })
        assertEquals(listOf("Ana", "Filipa", "Duda", "Eva", "Caro", "Bia"), rows.sortedBy(FullStatsColumn.POSITION, descending = true).map { it.player.name })
    }

    // Every ranking with two decimals (the developer, 2026-11-04).
    @Test
    fun rankingsShowTwoDecimals() {
        assertEquals(listOf("8.90", "7.67", "10.00", "0.05"), listOf(8.9, 7.666, 10.0, 0.049).map { it.twoDecimals() })
    }
}
