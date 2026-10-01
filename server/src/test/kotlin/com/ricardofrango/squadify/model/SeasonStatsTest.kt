package com.ricardofrango.squadify.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class SeasonStatsTest {

    private fun row(
        id: String,
        type: MatchType?,
        started: Boolean = true,
        minutes: Int = 70,
        goals: Int = 0,
        assists: Int = 0,
        yellow: Int = 0,
        red: Int = 0,
        rating: Int? = null
    ) = PlayerSeasonMatch(
        matchId = id, dateTime = Instant.fromEpochMilliseconds(id.hashCode().toLong()), opponentName = "X",
        matchType = type, started = started, minutesPlayed = minutes, goals = goals, assists = assists,
        ownGoals = 0, yellowCards = yellow, redCards = red, rating = rating
    )

    private val rows = listOf(
        row("m1", MatchType.CHAMPIONSHIP, goals = 2, rating = 9),
        row("m2", MatchType.TOURNAMENT, minutes = 40, assists = 1, yellow = 1, rating = 8),
        row("m3", MatchType.FRIENDLY, started = false, minutes = 35, goals = 1, rating = 7),
        row("m4", null, started = false, minutes = 20)
    )

    @Test
    fun allCountsEveryMatchAndFlagsTheUntypedOnes() {
        val t = rows.totals(StatsFilter.ALL)
        assertEquals(4, t.apps)
        assertEquals(2, t.starts)
        assertEquals(165, t.minutes)
        assertEquals(3, t.goals)
        assertEquals(1, t.assists)
        assertEquals(1, t.yellowCards)
        assertEquals(3, t.ratedMatches)
        assertEquals(8.0, t.averageRating)
        assertEquals(1, t.untypedMatches)
    }

    @Test
    fun competitiveIsChampionshipPlusTournamentAndDropsUntyped() {
        val t = rows.totals(StatsFilter.COMPETITIVE)
        assertEquals(2, t.apps)
        assertEquals(2, t.goals)
        assertEquals(8.5, t.averageRating)
        assertEquals(0, t.untypedMatches)
    }

    @Test
    fun friendlyOnlyAndNoRatingsMeansNoAverage() {
        assertEquals(1, rows.totals(StatsFilter.FRIENDLY).apps)
        val unrated = listOf(row("a", MatchType.FRIENDLY), row("b", MatchType.FRIENDLY)).totals(StatsFilter.FRIENDLY)
        assertNull(unrated.averageRating)
        assertEquals(0, unrated.ratedMatches)
    }

    @Test
    fun averageRatingIsRoundedToOneDecimal() {
        val t = listOf(
            row("a", MatchType.CHAMPIONSHIP, rating = 7),
            row("b", MatchType.CHAMPIONSHIP, rating = 8),
            row("c", MatchType.CHAMPIONSHIP, rating = 8)
        ).totals(StatsFilter.ALL)
        assertEquals(7.7, t.averageRating)
    }

    // ---- Team ----

    private fun match(id: String, day: Long, type: MatchType?, us: Int, them: Int) =
        TeamSeasonMatch(id, Instant.fromEpochMilliseconds(day * 86_400_000), "Opp $id", type, us, them)

    private fun line(matchId: String, playerId: String, minutes: Int = 60, goals: Int = 0, assists: Int = 0, rating: Int? = null) =
        TeamSeasonPlayerLine(matchId, playerId, minutes, goals, assists, rating)

    @Test
    fun recordCountsResultsGoalsAndFormNewestFirst() {
        val record = TeamSeasonRecord(
            teamId = "t", seasonName = "2026/27", canSeeRatings = true, playerLines = emptyList(), players = emptyList(),
            matches = listOf(
                match("a", 1, MatchType.CHAMPIONSHIP, 3, 1),
                match("b", 2, MatchType.FRIENDLY, 1, 1),
                match("c", 3, MatchType.CHAMPIONSHIP, 0, 2),
                match("d", 4, null, 2, 0),
                match("e", 5, MatchType.TOURNAMENT, 1, 0),
                match("f", 6, MatchType.CHAMPIONSHIP, 2, 2)
            )
        )
        val all = record.totals(StatsFilter.ALL)
        assertEquals(6, all.played)
        assertEquals(3, all.won)
        assertEquals(2, all.drawn)
        assertEquals(1, all.lost)
        assertEquals(9, all.goalsFor)
        assertEquals(6, all.goalsAgainst)
        assertEquals(3, all.goalDifference)
        assertEquals(listOf("f", "e", "d", "c", "b"), all.form.map { it.matchId })
        assertEquals(FormResult.DRAW, all.form.first().result)
        assertEquals(1, all.untypedMatches)

        val competitive = record.totals(StatsFilter.COMPETITIVE)
        assertEquals(4, competitive.played)
        assertEquals(0, competitive.untypedMatches)
        assertEquals(listOf("f", "e", "c", "a"), competitive.form.map { it.matchId })
    }

    @Test
    fun leadersShareRanksOnTiesSkipZerosAndCapAtThree() {
        val record = TeamSeasonRecord(
            teamId = "t", seasonName = "2026/27", canSeeRatings = true,
            matches = listOf(match("a", 1, MatchType.CHAMPIONSHIP, 4, 0), match("b", 2, MatchType.FRIENDLY, 3, 0)),
            players = listOf(
                TeamSeasonPlayer("p1", "Tomás"), TeamSeasonPlayer("p2", "Gil"),
                TeamSeasonPlayer("p3", "Francisco"), TeamSeasonPlayer("p4", "Bruno"), TeamSeasonPlayer("p5", "Rui")
            ),
            playerLines = listOf(
                line("a", "p1", goals = 2, rating = 8), line("b", "p1", goals = 2, rating = 9),
                line("a", "p2", goals = 4, rating = 6),
                line("a", "p3", goals = 1, assists = 2), line("b", "p3", goals = 3, minutes = 70, rating = 7),
                line("a", "p4", assists = 1, minutes = 90), line("b", "p4", minutes = 90),
                line("a", "p5", goals = 1)
            )
        )
        val leaders = record.leaders(StatsFilter.ALL)
        // Three on 4 goals share #1, listed by name; Rui (1) falls outside the top three rows.
        assertEquals(listOf(1, 1, 1), leaders.goals.map { it.rank })
        assertEquals(listOf("Francisco", "Gil", "Tomás"), leaders.goals.map { it.name })
        // Zero-assist players are left out, so the list is shorter than three.
        assertEquals(listOf("Francisco" to 1, "Bruno" to 2), leaders.assists.map { it.name to it.rank })
        assertEquals("Bruno", leaders.minutes.first().name)
        // Avg rating needs two rated matches: only Tomás (8.5) qualifies.
        assertEquals(listOf("Tomás" to 8.5), leaders.averageRating.map { it.name to it.value })

        // Friendly only: match b.
        val friendly = record.leaders(StatsFilter.FRIENDLY)
        assertEquals(listOf("Francisco", "Tomás"), friendly.goals.map { it.name })
        assertTrue(friendly.averageRating.isEmpty())
    }

    @Test
    fun noRatingLeadersForCallersWhoCantSeeRatings() {
        val record = TeamSeasonRecord(
            teamId = "t", seasonName = "2026/27", canSeeRatings = false,
            matches = listOf(match("a", 1, MatchType.CHAMPIONSHIP, 1, 0), match("b", 2, MatchType.CHAMPIONSHIP, 1, 0)),
            players = listOf(TeamSeasonPlayer("p1", "Tomás")),
            playerLines = listOf(line("a", "p1", rating = 8), line("b", "p1", rating = 9))
        )
        assertTrue(record.leaders(StatsFilter.ALL).averageRating.isEmpty())
    }
}
