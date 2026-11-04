package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.StatsFilter
import com.ricardofrango.squadify.model.TeamSeasonMatch
import com.ricardofrango.squadify.model.TeamSeasonRecord
import com.ricardofrango.squadify.model.matchTallies
import com.ricardofrango.squadify.model.totals
import com.ricardofrango.squadify.presentation.ui.matchrecord.draftEvents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

// The live game's shots, fouls and corners (the developer, 2026-11-03), counted as in football: a goal
// is also a shot on goal, a shot on goal also a shot (the developer, 2026-11-04).
class MatchTalliesTest {
    private val at = Instant.fromEpochMilliseconds(0)
    private var seq = 0
    private fun event(type: EventType, playerId: String? = null, opponent: Boolean = false) =
        MatchEvent("e${seq++}", "m", type, 10, playerId = playerId, isOpponent = opponent, createdAt = at, period = 1)

    @Test
    fun bothSidesAreCountedAndTheOpponentsShotsAreUnknown() {
        val tallies = matchTallies(
            listOf(
                event(EventType.SHOT, "a"), event(EventType.SHOT, "b"),
                event(EventType.SHOT_ON_GOAL, "a"),
                // Our goals count as shots on goal, a goal without its scorer too; theirs don't count for anyone.
                event(EventType.GOAL, "a"), event(EventType.GOAL), event(EventType.GOAL, opponent = true),
                event(EventType.CORNER), event(EventType.CORNER), event(EventType.CORNER, opponent = true),
                event(EventType.FOUL, "b"), event(EventType.FOUL, opponent = true), event(EventType.FOUL, opponent = true)
            )
        )
        assertEquals(listOf(5, 3, 2, 1), tallies.ours.let { listOf(it.shots, it.shotsOnGoal, it.corners, it.fouls) })
        assertNull(tallies.theirs.shots)
        assertNull(tallies.theirs.shotsOnGoal)
        assertEquals(listOf(1, 2), tallies.theirs.let { listOf(it.corners, it.fouls) })
        assertTrue(tallies.recorded)
    }

    @Test
    fun aMatchWithOnlyGoalsHasNothingRecorded() {
        assertFalse(matchTallies(listOf(event(EventType.GOAL, "a"), event(EventType.YELLOW_CARD, "a"))).recorded)
    }

    @Test
    fun theSeasonRecordAddsUpEachSidesCornersAndFouls() {
        fun match(id: String, cornersFor: Int, foulsAgainst: Int) =
            TeamSeasonMatch(id, at, "Opp", ourScore = 1, opponentScore = 0, cornersFor = cornersFor, cornersAgainst = 1, foulsFor = 2, foulsAgainst = foulsAgainst)
        val record = TeamSeasonRecord("t", "s", "26/27", listOf(match("m1", 4, 3), match("m2", 2, 5)), emptyList(), emptyList(), canSeeRatings = false)
        val t = record.totals(StatsFilter.ALL)
        assertEquals(listOf(6, 2, 4, 8), listOf(t.cornersFor, t.cornersAgainst, t.foulsFor, t.foulsAgainst))
        assertTrue(t.hasTallies())
    }

    @Test
    fun theMatchRecordEditorLeavesThemOut() {
        val drafts = draftEvents(listOf(event(EventType.GOAL, "a"), event(EventType.SHOT, "a"), event(EventType.CORNER), event(EventType.FOUL, opponent = true)))
        assertEquals(listOf(EventType.GOAL), drafts.map { it.type })
    }
}
