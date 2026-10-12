package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.model.FormResult
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.OpponentDetails
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.SeasonType
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.presentation.ui.opponents.allTimeMatchCount
import com.ricardofrango.squadify.presentation.ui.opponents.defaultHeadToHeadSeasonId
import com.ricardofrango.squadify.presentation.ui.opponents.hasFinishedMatches
import com.ricardofrango.squadify.presentation.ui.opponents.headToHead
import com.ricardofrango.squadify.presentation.ui.opponents.headToHeadSeasons
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

// Opponent details' head-to-head (design 38i): one season, across all our teams.
class OpponentHeadToHeadTest {
    private val lastSeason = Season("s1", "c", "2025/26", SeasonType.EUROPEAN_YEAR, LocalDate(2025, 9, 1), LocalDate(2026, 6, 30), false)
    private val thisSeason = Season("s2", "c", "2026/27", SeasonType.EUROPEAN_YEAR, LocalDate(2026, 9, 1), LocalDate(2027, 6, 30), true)
    private val olderSeason = Season("s0", "c", "2024/25", SeasonType.EUROPEAN_YEAR, LocalDate(2024, 9, 1), LocalDate(2025, 6, 30), false)

    private fun match(id: String, team: String, day: Int, status: MatchStatus = MatchStatus.FINISHED) =
        Match(id, "c", team, "o1", dateTime = Instant.fromEpochSeconds(day * 86_400L), status = status)

    private val details = OpponentDetails(
        opponent = Opponent("o1", "c", "AD Fontelas"),
        clubName = "CD Ribamar",
        matches = listOf(
            match("m6", "t2a", 60, MatchStatus.SCHEDULED),
            match("m5", "t2b", 50, MatchStatus.CANCELLED),
            match("m4", "t2b", 40),
            match("m3", "t2a", 30),
            match("m2", "t2a", 20),
            match("m1", "t1", 10)
        ),
        scores = listOf(MatchScore("m4", 1, 1), MatchScore("m3", 0, 2), MatchScore("m2", 3, 1), MatchScore("m1", 5, 0)),
        teams = listOf(
            Team("t1", "c", "ag", "s1", "Sub-15 A", active = true),
            Team("t2a", "c", "ag", "s2", "Sub-15 A", active = true),
            Team("t2b", "c", "ag", "s2", "Sub-15 B", active = true)
        ),
        seasons = listOf(olderSeason, lastSeason, thisSeason)
    )

    @Test
    fun theRecordCountsTheSeasonsFinishedMatchesAcrossAllTeams() {
        val t = headToHead(details, "s2")
        assertEquals(3, t.played)
        assertEquals(listOf(1, 1, 1), listOf(t.won, t.drawn, t.lost))
        assertEquals(4, t.goalsFor)
        assertEquals(4, t.goalsAgainst)
        // Most recent first; the screen reverses it.
        assertEquals(listOf(FormResult.DRAW, FormResult.LOSS, FormResult.WIN), t.form.map { it.result })
    }

    @Test
    fun anotherSeasonHasItsOwnRecord() {
        val t = headToHead(details, "s1")
        assertEquals(1, t.played)
        assertEquals(5, t.goalsFor)
    }

    @Test
    fun theSelectorOffersSeasonsWithMatchesNewestFirst() {
        assertEquals(listOf("s2", "s1"), headToHeadSeasons(details, defaultId = "s2").map { it.id })
    }

    @Test
    fun theDefaultSeasonIsTheCurrentOneElseTheLatest() {
        val seasons = details.seasons
        assertEquals("s2", defaultHeadToHeadSeasonId(seasons, LocalDate(2026, 10, 12)))
        // Between seasons (July): the latest one.
        assertEquals("s2", defaultHeadToHeadSeasonId(seasons, LocalDate(2026, 7, 15)))
        assertEquals("s1", defaultHeadToHeadSeasonId(seasons, LocalDate(2026, 1, 15)))
    }

    @Test
    fun allTimeLeavesOutCancelledMatches() {
        assertEquals(5, allTimeMatchCount(details))
    }

    @Test
    fun withNoFinishedMatchTheHeadToHeadIsEmpty() {
        assertTrue(hasFinishedMatches(details))
        assertFalse(hasFinishedMatches(details.copy(matches = listOf(match("m6", "t2a", 60, MatchStatus.SCHEDULED)))))
    }
}
