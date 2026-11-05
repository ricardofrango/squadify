package com.ricardofrango.squadify.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FullStatsTest {

    private val utc = TimeZone.UTC
    private fun day(d: Int) = LocalDate(2026, 10, d)
    private fun match(id: String, team: String, d: Int, us: Int, them: Int, type: MatchType = MatchType.CHAMPIONSHIP) =
        FullStatsMatch(id, team, day(d).atStartOfDayIn(utc), type, us, them)
    private fun line(match: String, player: String, minutes: Int = 60, goals: Int = 0, rating: Int? = null, yellow: Int = 0) =
        FullStatsLine(match, player, started = minutes > 0, minutesPlayed = minutes, goals = goals, assists = 0, yellowCards = yellow, redCards = 0, rating = rating)
    private fun player(id: String, name: String) = TeamSeasonPlayer(id, name)

    private val rodrigo = player("p1", "Rodrigo Costa")
    private val tomas = player("p2", "Tomás Silva")
    private val afonso = player("p3", "Afonso Pereira")

    private fun data(canSeeRatings: Boolean = true) = FullStatsData(
        title = "Sub-13", ageGroupId = "ag", seasonId = "s", seasonName = "2026/27", seasonStart = LocalDate(2026, 9, 1), seasonEnd = LocalDate(2027, 6, 30),
        teams = listOf(FullStatsTeam("A", "Sub-13 A"), FullStatsTeam("B", "Sub-13 B")),
        matches = listOf(
            match("m1", "A", 17, 3, 1),
            match("m2", "A", 18, 0, 2, MatchType.FRIENDLY),
            match("m3", "B", 18, 1, 1)
        ),
        lines = listOf(
            line("m1", "p1", goals = 2, rating = 7),
            line("m2", "p1", goals = 1, rating = 5, yellow = 1),
            line("m1", "p2", minutes = 0), // unused sub: not a game played
            line("m3", "p2", rating = 8),
            line("m3", "p3", rating = 6)
        ),
        trainings = listOf(
            FullStatsTraining("t1", day(19), listOf(TrainingRating("p1", 6.0), TrainingRating("p2", 0.0))),
            FullStatsTraining("t2", day(20), listOf(TrainingRating("p1", 8.0), TrainingRating("p2", 7.0))),
            FullStatsTraining("t3", day(21), listOf(TrainingRating("p1", 9.0)))
        ),
        players = listOf(rodrigo, tomas, afonso),
        canSeeRatings = canSeeRatings
    )

    private fun FullStatsTable.row(id: String) = rows.single { it.player.id == id }

    @Test
    fun theFinalRankingAveragesEveryGameAndTrainingRatingTogether() {
        val t = data().table(FullStatsQuery(), utc)
        val r = t.row("p1")
        assertEquals(6.0, r.gamesRating) // (7 + 5) / 2
        assertEquals(7.67, r.trainingRating) // (6 + 8 + 9) / 3, two decimals
        // (7 + 5 + 6 + 8 + 9) / 5 = 7.0 -- not the average of the two averages (6.85)
        assertEquals(7.0, r.finalRating)
        // An absent training counts as a 0.
        assertEquals(3.5, t.row("p2").trainingRating) // (0 + 7) / 2
        assertEquals(5.0, t.row("p2").finalRating) // (8 + 0 + 7) / 3
        assertEquals(3, t.games)
        assertEquals(3, t.trainings)
    }

    @Test
    fun missingATrainingLowersTheTrainingAndFinalRanking() {
        // The developer's example (2026-10-29): 8, 8 and Absent is (8 + 8 + 0) / 3, not 8.
        val trainings = listOf(8.0, 8.0, 0.0).mapIndexed { i, r -> FullStatsTraining("t$i", day(19 + i), listOf(TrainingRating("p3", r))) }
        val t = data().copy(trainings = trainings).table(FullStatsQuery(), utc)
        val r = t.row("p3")
        assertEquals(5.33, r.trainingRating)
        assertEquals(5.5, r.finalRating) // (6 + 8 + 8 + 0) / 4
        assertEquals(4, r.ratings)
    }

    @Test
    fun wonDrawnLostAndTotalsCountOnlyTheGamesThePlayerPlayed() {
        val t = data().table(FullStatsQuery(), utc)
        val r = t.row("p1")
        assertEquals(listOf(2, 3, 120, 1, 0, 1, 1), listOf(r.games, r.goals, r.minutes, r.won, r.drawn, r.lost, r.yellowCards))
        // p2 sat on the bench in m1 (a win): only the draw in m3 counts.
        val p2 = t.row("p2")
        assertEquals(listOf(1, 0, 1, 0), listOf(p2.games, p2.won, p2.drawn, p2.lost))
    }

    @Test
    fun aPositionOnlyListsThePlayersInIt() {
        val positioned = data().copy(players = listOf(rodrigo.copy(position = PlayerPosition.STRIKER), tomas.copy(position = PlayerPosition.DEFENDER), afonso))
        assertEquals(listOf("p1"), positioned.table(FullStatsQuery(position = PlayerPosition.STRIKER), utc).rows.map { it.player.id })
        // A player without a position only shows with every position.
        assertTrue(positioned.table(FullStatsQuery(position = PlayerPosition.GOALKEEPER), utc).rows.isEmpty())
        assertEquals(3, positioned.table(FullStatsQuery(), utc).rows.size)
        // Combined with the team: B's players (p2, p3) who are defenders.
        assertEquals(listOf("p2"), positioned.table(FullStatsQuery(teamId = "B", position = PlayerPosition.DEFENDER), utc).rows.map { it.player.id })
        // The games and trainings in the period don't depend on the position.
        assertEquals(3, positioned.table(FullStatsQuery(position = PlayerPosition.GOALKEEPER), utc).games)
    }

    @Test
    fun aTeamOnlyListsThePlayersWhoPlayedForIt() {
        // Team A selected (as when entering from it): p2 was only an unused sub for A, p3 only played for B.
        val team = data().table(FullStatsQuery(teamId = "A"), utc)
        assertEquals(listOf("p1"), team.rows.map { it.player.id })
        assertEquals(2, team.games)
        // The age group lists its whole pool; narrowed to team B, only who played for B.
        assertEquals(3, data().table(FullStatsQuery(), utc).rows.size)
        val b = data().table(FullStatsQuery(teamId = "B"), utc)
        assertEquals(setOf("p2", "p3"), b.rows.map { it.player.id }.toSet())
        assertEquals(1, b.games)
    }

    @Test
    fun theDateRangeAndMatchTypeNarrowTheRows() {
        // 18-20 Oct: m2 and m3 (both on the 18th), trainings t1 and t2.
        val t = data().table(FullStatsQuery(from = day(18), to = day(20)), utc)
        assertEquals(2, t.games)
        assertEquals(2, t.trainings)
        assertEquals(5.0, t.row("p1").gamesRating)
        assertEquals(7.0, t.row("p1").trainingRating)
        // Friendlies only: m2. Trainings still count, whatever the match type.
        val friendly = data().table(FullStatsQuery(type = StatsFilter.FRIENDLY), utc)
        assertEquals(1, friendly.games)
        assertEquals(1, friendly.row("p1").goals)
        assertEquals(3, friendly.trainings)
        // Nothing in the range: the empty state.
        assertTrue(data().table(FullStatsQuery(from = day(25), to = day(26)), utc).isEmpty)
    }

    @Test
    fun withoutRatingAccessEveryRankingIsEmpty() {
        val r = data(canSeeRatings = false).table(FullStatsQuery(), utc).row("p1")
        assertNull(r.gamesRating)
        assertNull(r.trainingRating)
        assertNull(r.finalRating)
        assertEquals(3, r.goals)
    }

    @Test
    fun sortingPutsUnratedPlayersLastEitherWay() {
        val rows = data().table(FullStatsQuery(from = day(17), to = day(17)), utc).rows
        // Only p1 has a rating on the 17th.
        assertEquals(listOf("p1", "p3", "p2"), rows.sortedBy(FullStatsColumn.FINAL_RATING, descending = true).map { it.player.id })
        assertEquals(listOf("p1", "p3", "p2"), rows.sortedBy(FullStatsColumn.FINAL_RATING, descending = false).map { it.player.id })
        // Ties by name.
        assertEquals(listOf("p3", "p1", "p2"), rows.sortedBy(FullStatsColumn.ASSISTS, descending = true).map { it.player.id })
        assertEquals(listOf("p1", "p3", "p2"), rows.sortedBy(FullStatsColumn.GOALS, descending = true).map { it.player.id })
    }

    @Test
    fun theAgeGroupLeadersRankOnGamesAndTrainingsTogether() {
        val leaders = data().table(FullStatsQuery(), utc).leaders()
        // p1's 7.0 tops p2's 5.0, (8 + 0 + 7) / 3 with the missed training; p3 has a single rating, short of the two needed.
        assertEquals(listOf("p1", "p2"), leaders.averageRating.map { it.playerId })
        assertEquals(listOf(1, 2), leaders.averageRating.map { it.rank })
        // Players on zero are left out.
        assertEquals(listOf("p1"), leaders.goals.map { it.playerId })
        assertEquals(listOf("p1", "p3", "p2"), leaders.minutes.map { it.playerId })
        assertEquals(listOf(1, 2, 2), leaders.minutes.map { it.rank }) // a tie, by name
        // No rating access, no ranking.
        assertTrue(data(canSeeRatings = false).table(FullStatsQuery(), utc).leaders().averageRating.isEmpty())
    }

    @Test
    fun noColumnSortsByThePlayersName() {
        val rows = data().table(FullStatsQuery(), utc).rows
        // Afonso, Rodrigo, Tomás -- and back.
        assertEquals(listOf("p3", "p1", "p2"), rows.sortedBy(null, descending = false).map { it.player.id })
        assertEquals(listOf("p2", "p1", "p3"), rows.sortedBy(null, descending = true).map { it.player.id })
    }
}
