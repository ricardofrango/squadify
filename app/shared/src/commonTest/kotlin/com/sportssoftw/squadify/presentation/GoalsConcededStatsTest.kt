package com.sportssoftw.squadify.presentation

import com.sportssoftw.squadify.model.FullStatsColumn
import com.sportssoftw.squadify.model.FullStatsData
import com.sportssoftw.squadify.model.FullStatsLine
import com.sportssoftw.squadify.model.FullStatsMatch
import com.sportssoftw.squadify.model.FullStatsQuery
import com.sportssoftw.squadify.model.FullStatsTeam
import com.sportssoftw.squadify.model.PlayerSeasonMatch
import com.sportssoftw.squadify.model.StatsFilter
import com.sportssoftw.squadify.model.TeamSeasonPlayer
import com.sportssoftw.squadify.model.sortedBy
import com.sportssoftw.squadify.model.table
import com.sportssoftw.squadify.model.totals
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

// The goals the opponent scored with a player in goal (TECHNICAL_DECISIONS §86), summed in Full stats and in a
// player's season totals; only the games he played count.
class GoalsConcededStatsTest {
    private val at = Instant.fromEpochMilliseconds(1_790_000_000_000L)
    private val players = listOf(TeamSeasonPlayer("gk", "Rui"), TeamSeasonPlayer("d", "Dani"))
    private val data = FullStatsData(
        title = "Sub-12", ageGroupId = "ag", seasonId = "s1", seasonName = "2026/27",
        seasonStart = LocalDate(2026, 9, 1), seasonEnd = LocalDate(2027, 6, 30),
        teams = listOf(FullStatsTeam("t1", "A")),
        matches = listOf(FullStatsMatch("m1", "t1", at, null, 1, 3), FullStatsMatch("m2", "t1", at, null, 0, 1)),
        lines = listOf(
            FullStatsLine("m1", "gk", true, 60, 0, 0, 0, 0, goalsConceded = 2),
            FullStatsLine("m1", "d", true, 60, 0, 0, 0, 0, goalsConceded = 1),
            FullStatsLine("m2", "gk", true, 60, 0, 0, 0, 0, goalsConceded = 1),
            // Didn't play: not counted, whatever the line says.
            FullStatsLine("m2", "d", false, 0, 0, 0, 0, 0, goalsConceded = 5)
        ),
        trainings = emptyList(), players = players, canSeeRatings = false
    )

    @Test
    fun fullStatsSumsTheGoalsConcededOfTheGamesPlayedAndSortsByThem() {
        val rows = data.table(FullStatsQuery()).rows
        assertEquals(3, rows.single { it.player.id == "gk" }.goalsConceded)
        assertEquals(1, rows.single { it.player.id == "d" }.goalsConceded)
        assertEquals(listOf("gk", "d"), rows.sortedBy(FullStatsColumn.GOALS_CONCEDED, descending = true).map { it.player.id })
    }

    @Test
    fun aPlayersSeasonTotalsSumTheGoalsConceded() {
        fun match(id: String, minutes: Int, conceded: Int) = PlayerSeasonMatch(
            matchId = id, dateTime = at, opponentName = "Rival", started = minutes > 0, minutesPlayed = minutes,
            goals = 0, assists = 0, ownGoals = 0, yellowCards = 0, redCards = 0, goalsConceded = conceded
        )
        val totals = listOf(match("m1", 60, 2), match("m2", 60, 1), match("m3", 0, 4)).totals(StatsFilter.ALL)
        assertEquals(3, totals.goalsConceded)
    }
}
