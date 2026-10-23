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
    fun officialAndTournamentAreSeparateAndDropUntyped() {
        val official = rows.totals(StatsFilter.OFFICIAL)
        assertEquals(1, official.apps)
        assertEquals(2, official.goals)
        assertEquals(9.0, official.averageRating)
        assertEquals(0, official.untypedMatches)
        val tournament = rows.totals(StatsFilter.TOURNAMENT)
        assertEquals(1, tournament.apps)
        assertEquals(1, tournament.assists)
        assertEquals(8.0, tournament.averageRating)
        assertEquals(0, tournament.untypedMatches)
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
            teamId = "t", seasonId = "s", seasonName = "2026/27", canSeeRatings = true, playerLines = emptyList(), players = emptyList(),
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

        val official = record.totals(StatsFilter.OFFICIAL)
        assertEquals(3, official.played)
        assertEquals(0, official.untypedMatches)
        assertEquals(listOf("f", "c", "a"), official.form.map { it.matchId })
        assertEquals(listOf("e"), record.totals(StatsFilter.TOURNAMENT).form.map { it.matchId })
    }

    @Test
    fun leadersShareRanksOnTiesSkipZerosAndShowUpToFive() {
        val record = TeamSeasonRecord(
            teamId = "t", seasonId = "s", seasonName = "2026/27", canSeeRatings = true,
            matches = listOf(match("a", 1, MatchType.CHAMPIONSHIP, 4, 0), match("b", 2, MatchType.FRIENDLY, 3, 0)),
            players = listOf(
                TeamSeasonPlayer("p1", "Tomás", photoUrl = "/players/p1/photo/a.png"), TeamSeasonPlayer("p2", "Gil"),
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
        // Three on 4 goals share #1, listed by name; Rui (1) is next, at #4 (up to five are shown).
        assertEquals(listOf(1, 1, 1, 4), leaders.goals.map { it.rank })
        assertEquals(listOf("Francisco", "Gil", "Tomás", "Rui"), leaders.goals.map { it.name })
        // Each entry keeps the player's photo, for the leaders card.
        assertEquals(listOf(null, null, "/players/p1/photo/a.png", null), leaders.goals.map { it.photoUrl })
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
            teamId = "t", seasonId = "s", seasonName = "2026/27", canSeeRatings = false,
            matches = listOf(match("a", 1, MatchType.CHAMPIONSHIP, 1, 0), match("b", 2, MatchType.CHAMPIONSHIP, 1, 0)),
            players = listOf(TeamSeasonPlayer("p1", "Tomás")),
            playerLines = listOf(line("a", "p1", rating = 8), line("b", "p1", rating = 9))
        )
        assertTrue(record.leaders(StatsFilter.ALL).averageRating.isEmpty())
    }

    @Test
    fun anUnusedSubstituteIsNotAnAppearance() {
        val withBench = rows + row("m5", MatchType.CHAMPIONSHIP, started = false, minutes = 0)
        val t = withBench.totals(StatsFilter.ALL)
        assertEquals(4, t.apps) // m5 doesn't count
        assertEquals(listOf("m1", "m2", "m3", "m4"), withBench.inFilter(StatsFilter.ALL).map { it.matchId })
    }

    @Test
    fun squadListsOnlyPlayersWhoPlayedAndKeepsTheBenchApart() {
        val record = TeamSeasonRecord(
            teamId = "t", seasonId = "s", seasonName = "2026/27", canSeeRatings = true,
            matches = listOf(match("a", 1, MatchType.CHAMPIONSHIP, 2, 0), match("b", 2, MatchType.FRIENDLY, 1, 1)),
            players = listOf(
                TeamSeasonPlayer("p1", "Tomás", 10, PlayerPosition.MIDFIELD), TeamSeasonPlayer("p2", "Gil", 6),
                TeamSeasonPlayer("p3", "Caetano", 14), TeamSeasonPlayer("p4", "Bruno", 9)
            ),
            playerLines = listOf(
                line("a", "p1", minutes = 70, goals = 2, rating = 8).copy(started = true),
                line("b", "p1", minutes = 70, rating = 6).copy(started = true),
                line("a", "p2", minutes = 20, assists = 1, rating = 7),
                line("b", "p2", minutes = 0), // unused sub in b
                line("a", "p3", minutes = 0), line("b", "p3", minutes = 0), // called up twice, never on
                line("b", "p4", minutes = 35, goals = 1)
            )
        )
        val all = record.squad(StatsFilter.ALL)
        assertEquals(listOf("Tomás", "Bruno", "Gil"), all.used.map { it.player.name }) // apps 2, then 1s by name
        val tomas = all.used.first()
        assertEquals(2, tomas.apps)
        assertEquals(140, tomas.minutes)
        assertEquals(7.0, tomas.averageRating)
        assertEquals(1, all.used.first { it.player.name == "Gil" }.apps) // the 0-minute line isn't an app
        assertEquals(listOf("Caetano" to 2), all.benchOnly.map { it.player.name to it.calledUp })

        assertEquals(listOf("Tomás", "Bruno", "Gil"), record.squad(StatsFilter.ALL, SquadSort.GOALS).used.map { it.player.name })
        assertEquals(listOf("Gil", "Tomás", "Bruno"), record.squad(StatsFilter.ALL, SquadSort.RATING).used.map { it.player.name })
        assertEquals(listOf("Bruno", "Gil", "Tomás"), record.squad(StatsFilter.ALL, SquadSort.NAME).used.map { it.player.name })

        // Friendly only (match b): Gil sat on the bench there, so he moves to the bench group.
        val friendly = record.squad(StatsFilter.FRIENDLY)
        assertEquals(listOf("Bruno", "Tomás"), friendly.used.map { it.player.name }.sorted())
        assertEquals(setOf("Caetano", "Gil"), friendly.benchOnly.map { it.player.name }.toSet())
    }

    // ---- Age group (design 33) ----

    private fun team(id: String, matches: List<TeamSeasonMatch>, lines: List<TeamSeasonPlayerLine>) = TeamSeasonRecord(
        teamId = id, seasonId = "s", seasonName = "2026/27", matches = matches, playerLines = lines,
        players = emptyList(), canSeeRatings = true, teamName = "Sub-15 $id"
    )

    private val ageGroup = AgeGroupSeasonRecord(
        ageGroupId = "ag",
        canSeeRatings = true,
        pool = listOf(
            TeamSeasonPlayer("p1", "Ana"), TeamSeasonPlayer("p2", "Bia"), TeamSeasonPlayer("p3", "Caio"),
            TeamSeasonPlayer("p4", "Duda"), TeamSeasonPlayer("p5", "Edu")
        ),
        teams = listOf(
            team(
                "A",
                listOf(match("a1", 1, MatchType.CHAMPIONSHIP, 3, 1), match("a2", 2, MatchType.FRIENDLY, 0, 0)),
                listOf(
                    line("a1", "p1", minutes = 200, rating = 8).copy(started = true), line("a2", "p1", minutes = 150, rating = 6).copy(started = true),
                    line("a1", "p2", minutes = 120), line("a2", "p4", minutes = 0) // p4 unused sub
                )
            ),
            team(
                "B",
                listOf(match("b1", 3, MatchType.CHAMPIONSHIP, 1, 2)),
                listOf(line("b1", "p2", minutes = 40), line("b1", "p3", minutes = 30), line("b1", "p9", minutes = 70)) // p9 not in the pool
            )
        )
    )

    @Test
    fun combinedRecordMergesEveryTeam() {
        val all = ageGroup.combined(StatsFilter.ALL)
        assertEquals(3, all.played)
        assertEquals(listOf(1, 1, 1), listOf(all.won, all.drawn, all.lost))
        assertEquals(4, all.goalsFor)
        assertEquals(3, all.goalsAgainst)
        assertEquals(2, ageGroup.combined(StatsFilter.OFFICIAL).played)
        assertEquals(0, ageGroup.combined(StatsFilter.TOURNAMENT).played)
    }

    @Test
    fun poolMinutesCountsEachPlayerOnceAcrossTeams() {
        val m = ageGroup.poolMinutes(StatsFilter.ALL)
        assertEquals(5, m.totalPlayers)
        assertEquals(listOf("Ana"), m.bands.getValue(MinutesBand.LOTS).map { it.name })   // 350
        assertEquals(listOf("Bia"), m.bands.getValue(MinutesBand.SOME).map { it.name })   // 120 + 40, both teams
        assertEquals(listOf("Caio"), m.bands.getValue(MinutesBand.LITTLE).map { it.name }) // 30
        assertEquals(listOf("Duda", "Edu"), m.bands.getValue(MinutesBand.NONE).map { it.name }) // unused sub, never called up
        assertEquals(1, m.playedForSeveralTeams)
        // Official only: Ana keeps just a1's 200.
        assertEquals(listOf("Ana"), ageGroup.poolMinutes(StatsFilter.OFFICIAL).bands.getValue(MinutesBand.SOME).map { it.name }.filter { it == "Ana" })
    }

    @Test
    fun memberSeasonsAndTeamFiguresIgnoreUnusedSubstitutes() {
        val members = ageGroup.memberSeasons()
        assertEquals(MemberSeason(apps = 2, minutes = 160, teamIds = listOf("A", "B")), members["p2"])
        assertNull(members["p4"])
        val a = ageGroup.teams.first()
        assertEquals(2, a.playersUsed())
        assertEquals(7.0, a.averageRating())
    }
}
