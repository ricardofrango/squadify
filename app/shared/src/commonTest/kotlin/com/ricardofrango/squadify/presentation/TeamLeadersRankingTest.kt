package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.model.FullStatsData
import com.ricardofrango.squadify.model.FullStatsLine
import com.ricardofrango.squadify.model.FullStatsMatch
import com.ricardofrango.squadify.model.FullStatsTeam
import com.ricardofrango.squadify.model.FullStatsTraining
import com.ricardofrango.squadify.model.TeamSeasonMatch
import com.ricardofrango.squadify.model.TeamSeasonPlayer
import com.ricardofrango.squadify.model.TeamSeasonPlayerLine
import com.ricardofrango.squadify.model.TeamSeasonRecord
import com.ricardofrango.squadify.model.TrainingRating
import com.ricardofrango.squadify.presentation.ui.teamdetails.TeamRecordUiState
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

// Team leaders' ranking is the team's Full stats final ranking: games and trainings together, a
// missed training counting as 0 (the developer, 2026-10-29).
class TeamLeadersRankingTest {
    private val at = Instant.fromEpochMilliseconds(1_790_000_000_000L)
    private val players = listOf(TeamSeasonPlayer("p1", "Ana"), TeamSeasonPlayer("p2", "Bia"))
    private val record = TeamSeasonRecord(
        "t1", "s1", "2026/27",
        matches = listOf(TeamSeasonMatch("m1", at, "Rival", ourScore = 1, opponentScore = 0)),
        playerLines = listOf(TeamSeasonPlayerLine("m1", "p1", 60, 0, 0, rating = 8), TeamSeasonPlayerLine("m1", "p2", 60, 0, 0, rating = 7)),
        players = players,
        canSeeRatings = true
    )
    private val fullStats = FullStatsData(
        title = "Sub-13", ageGroupId = "ag", seasonId = "s1", seasonName = "2026/27",
        seasonStart = LocalDate(2026, 9, 1), seasonEnd = LocalDate(2027, 6, 30),
        teams = listOf(FullStatsTeam("t1", "A")),
        matches = listOf(FullStatsMatch("m1", "t1", at, null, 1, 0)),
        lines = listOf(FullStatsLine("m1", "p1", true, 60, 0, 0, 0, 0, 8), FullStatsLine("m1", "p2", true, 60, 0, 0, 0, 0, 7)),
        // Ana misses both trainings; Bia trains at 7.
        trainings = (1..2).map { d -> FullStatsTraining("tr$d", LocalDate(2026, 10, d), listOf(TrainingRating("p1", 0.0), TrainingRating("p2", 7.0))) },
        players = players,
        canSeeRatings = true
    )

    @Test
    fun missedTrainingsLowerTheTeamRanking() {
        val ranking = TeamRecordUiState.Loaded(record, fullStats = fullStats).leaders().averageRating
        // Bia (7 + 7 + 7) / 3 = 7.0 tops Ana's (8 + 0 + 0) / 3 = 2.67, though Ana had the better game.
        assertEquals(listOf("p2" to 7.0, "p1" to 2.67), ranking.map { it.playerId to it.value })
    }

    @Test
    fun withoutTheFullStatsDataTheRankingIsLeftOut() {
        val leaders = TeamRecordUiState.Loaded(record).leaders()
        assertTrue(leaders.averageRating.isEmpty())
        assertEquals(listOf("p1", "p2"), leaders.minutes.map { it.playerId })
    }
}
