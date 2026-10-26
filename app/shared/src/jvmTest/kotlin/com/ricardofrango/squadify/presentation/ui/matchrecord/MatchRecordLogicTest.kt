package com.ricardofrango.squadify.presentation.ui.matchrecord

import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.presentation.ui.matchdetails.previewFinishedDetails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class MatchRecordLogicTest {

    private val at = Instant.fromEpochMilliseconds(1_000)
    private fun event(id: String, type: EventType, playerId: String?, minute: Int = 10, related: String? = null, opponent: Boolean = false) =
        MatchEvent(id, "m1", type, minute, playerId = playerId, isOpponent = opponent, createdAt = at, period = 1, relatedEventId = related)

    private fun draft(type: EventType, playerId: String?, opponent: Boolean = false, assist: String? = null) =
        DraftEvent("k-$type-$playerId", null, type, 10, 1, playerId, opponent, assist)

    @Test
    fun theEditorListsGoalsAndCardsWithTheirAssistButNoSubstitutions() {
        val drafts = draftEvents(
            listOf(
                event("g1", EventType.GOAL, "p1"),
                event("a1", EventType.ASSIST, "p2", related = "g1"),
                event("in", EventType.SUBSTITUTION_IN, "p3"),
                event("out", EventType.SUBSTITUTION_OUT, "p4"),
                event("y1", EventType.YELLOW_CARD, "p4", minute = 5),
                event("lone", EventType.ASSIST, "p5", minute = 30)
            )
        )
        assertEquals(listOf("y1", "g1", "lone"), drafts.map { it.id })
        assertEquals("p2", drafts.single { it.id == "g1" }.assistPlayerId)
    }

    @Test
    fun anOwnGoalCountsForTheOtherSide() {
        val events = listOf(
            draft(EventType.GOAL, "p1"),
            draft(EventType.GOAL, null, opponent = true),
            draft(EventType.OWN_GOAL, null, opponent = true),
            draft(EventType.OWN_GOAL, "p2"),
            draft(EventType.YELLOW_CARD, "p3")
        )
        assertEquals(2 to 2, draftScore(events))
    }

    @Test
    fun aGoalsAssistTravelsWithItButNotWithTheOpponents() {
        val ours = draft(EventType.GOAL, "p1", assist = "p2").toRequest()
        assertEquals("p2", ours.assistPlayerId)
        val theirs = draft(EventType.GOAL, "p1", opponent = true, assist = "p2").toRequest()
        assertNull(theirs.playerId)
        assertNull(theirs.assistPlayerId)
    }

    @Test
    fun minutesAreWholeNumbersUpToTheMatchLengthAndBlankIsZero() {
        val goal = listOf(draft(EventType.GOAL, "p1"))
        assertNull(recordProblem(true, goal, mapOf("p1" to "50", "p2" to ""), 50, emptySet()))
        assertEquals(RecordProblem.MINUTES_INVALID, recordProblem(true, goal, mapOf("p1" to "51"), 50, emptySet()))
        assertEquals(RecordProblem.MINUTES_INVALID, recordProblem(true, goal, mapOf("p1" to "x"), 50, emptySet()))
    }

    @Test
    fun aGoalOrAssistNeedsAPlayerWhoPlayed() {
        val withAssist = listOf(draft(EventType.GOAL, "p1", assist = "p2"))
        assertEquals(RecordProblem.SCORER_DIDNT_PLAY, recordProblem(true, withAssist, mapOf("p1" to "50", "p2" to "0"), 50, emptySet()))
        // A card can go to the bench.
        assertNull(recordProblem(true, listOf(draft(EventType.YELLOW_CARD, "p2")), mapOf("p1" to "50"), 50, emptySet()))
        // Followed live: who played comes from the game.
        assertEquals(RecordProblem.SCORER_DIDNT_PLAY, recordProblem(false, withAssist, emptyMap(), 50, setOf("p1")))
        assertNull(recordProblem(false, withAssist, emptyMap(), 50, setOf("p1", "p2")))
    }

    @Test
    fun onlyAGameEnteredAfterwardsSendsItsMinutes() {
        val minutes = mapOf("p1" to "50", "p2" to "", "p3" to "20")
        val after = recordRequest(true, emptyList(), 25, minutes)
        assertEquals(25, after.halfLengthMinutes)
        assertEquals(mapOf("p1" to 50, "p3" to 20), after.minutesPlayed)
        val live = recordRequest(false, emptyList(), 25, minutes)
        assertNull(live.halfLengthMinutes)
        assertTrue(live.minutesPlayed.isEmpty())
    }

    @Test
    fun theResultCanBeEnteredOnceKickoffHasPassedFromTheSavedCallUp() {
        val finished = previewFinishedDetails(canSeeRecord = true).copy(canManageLiveMatch = true)
        assertTrue(finished.canEditRecord())
        assertFalse(finished.copy(canManageLiveMatch = false).canEditRecord())

        val scheduled = finished.copy(match = finished.match.copy(status = MatchStatus.SCHEDULED))
        val kickoff = scheduled.match.dateTime
        assertTrue(scheduled.canEnterResult(kickoff))
        assertFalse(scheduled.canEnterResult(Instant.fromEpochMilliseconds(kickoff.toEpochMilliseconds() - 1)))
        assertFalse(scheduled.copy(callUps = emptyList()).canEnterResult(kickoff))
        assertFalse(finished.canEnterResult(kickoff))
    }
}
