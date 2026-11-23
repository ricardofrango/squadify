package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.model.CallUpStatus
import com.sportssoftw.squadify.model.EventType
import com.sportssoftw.squadify.model.Match
import com.sportssoftw.squadify.model.MatchCallUp
import com.sportssoftw.squadify.model.MatchCallUpWithPlayer
import com.sportssoftw.squadify.model.MatchEvent
import com.sportssoftw.squadify.model.MatchStatus
import com.sportssoftw.squadify.model.Player
import com.sportssoftw.squadify.model.goalkeeperOnPitch
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

// Who is in goal when the opponent scores (TECHNICAL_DECISIONS §86): the pitch's slot 0 at that moment, from the
// saved arrangement and what happened since (a substitution, a red card, the arrangement saved again).
class GoalkeeperOnPitchTest {

    private val match = Match(
        "m-1", "club-1", "t-1", "opp-1", dateTime = Instant.fromEpochMilliseconds(1),
        status = MatchStatus.LIVE, teamSize = 3, halfLengthMinutes = 30, kickoffAt = Instant.fromEpochMilliseconds(1)
    )

    private fun callUp(playerId: String, slot: Int?, starting: Boolean) = MatchCallUpWithPlayer(
        MatchCallUp("c-$playerId", "m-1", playerId, CallUpStatus.CALLED_UP, null, starting, slot),
        mockk<Player> { every { position } returns null }
    )

    private fun event(type: EventType, minute: Int, playerId: String, at: Long) =
        MatchEvent("e-$type-$playerId", "m-1", type, minute, playerId = playerId, createdAt = Instant.fromEpochMilliseconds(at), period = 1)

    @Test
    fun whoeverStandsInGoalWhenTheGoalIsScored() {
        val callUps = listOf(callUp("gk", 0, true), callUp("d1", 1, true), callUp("a1", 2, true), callUp("bench", null, false))
        assertEquals("gk", goalkeeperOnPitch(match, callUps, emptyList()))
    }

    @Test
    fun aSubstituteWhoCameOnForTheGoalkeeperIsInGoal() {
        val callUps = listOf(callUp("gk", 0, true), callUp("d1", 1, true), callUp("a1", 2, true), callUp("sub", null, false))
        val events = listOf(
            event(EventType.SUBSTITUTION_OUT, 20, "gk", 1000), event(EventType.SUBSTITUTION_IN, 20, "sub", 1001)
        )
        assertEquals("sub", goalkeeperOnPitch(match, callUps, events))
    }

    @Test
    fun aSentOffGoalkeeperLeavesTheGoalEmptyUntilSomeoneIsPutThere() {
        val callUps = listOf(callUp("gk", 0, true), callUp("d1", 1, true), callUp("a1", 2, true))
        val sentOff = listOf(event(EventType.RED_CARD, 20, "gk", 1000))
        assertNull(goalkeeperOnPitch(match, callUps, sentOff, lineupSavedAt = null))

        // The coach moves a defender into goal: the arrangement saved after the red card has them in slot 0.
        val rearranged = listOf(callUp("gk", null, true), callUp("d1", 0, true), callUp("a1", 2, true))
        assertEquals("d1", goalkeeperOnPitch(match, rearranged, sentOff, lineupSavedAt = Instant.fromEpochMilliseconds(2000)))
    }

    @Test
    fun aGameWithNoSavedArrangementPlacesItsStartersByPosition() {
        // Kicked off before the pitch view: no slots saved. A goalkeeper by position goes in goal.
        val keeper = MatchCallUpWithPlayer(
            MatchCallUp("c-gk", "m-1", "gk", CallUpStatus.CALLED_UP, null, true, null),
            mockk<Player> { every { position } returns com.sportssoftw.squadify.model.PlayerPosition.GOALKEEPER }
        )
        val callUps = listOf(keeper, callUp("d1", null, true), callUp("a1", null, true))
        assertEquals("gk", goalkeeperOnPitch(match, callUps, emptyList()))
    }
}
