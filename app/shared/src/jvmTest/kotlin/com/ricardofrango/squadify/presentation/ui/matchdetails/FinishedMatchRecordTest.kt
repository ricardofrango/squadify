package com.ricardofrango.squadify.presentation.ui.matchdetails

import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.MatchPlayerStats
import com.ricardofrango.squadify.model.matchTimeline
import com.ricardofrango.squadify.model.TimelineChange
import com.ricardofrango.squadify.presentation.ui.livematch.teamSize
import kotlin.test.Test
import kotlin.test.assertEquals

class FinishedMatchRecordTest {

    private val staff = previewFinishedDetails(canSeeRecord = true)

    @Test
    fun onlyWhoPlayedHasALineAndTheRestAreUnused() {
        val lines = matchStatLines(staff)
        assertEquals(8, lines.played.size)
        // p9 (Gonçalo) was called up but never came on.
        assertEquals(listOf("p9"), lines.unused.map { it.id })
        // p8 came on: they played without starting.
        assert(lines.played.any { it.player.id == "p8" })
    }

    @Test
    fun theTableOpensByRankingOnceThereAreRatingsElseByMinutes() {
        val lines = matchStatLines(staff).played
        assertEquals(MatchStatColumn.RATING, defaultStatSort(lines))
        assertEquals("p7", lines.sortedByStat(MatchStatColumn.RATING).first().player.id)
        val unrated = lines.map { it.copy(stats = it.stats.copy(rating = null)) }
        assertEquals(MatchStatColumn.MINUTES, defaultStatSort(unrated))
    }

    @Test
    fun aColumnSortsHighestFirstTiesByNameAndNoColumnIsByName() {
        val lines = matchStatLines(staff).played
        // Every starter but p6 played all 52': by name among them (Ana, Beatriz, Miguel, Rui, Tiago, Tomás).
        assertEquals(listOf("p1", "p7", "p5", "p2", "p3", "p4"), lines.sortedByStat(MatchStatColumn.MINUTES).take(6).map { it.player.id })
        assertEquals("Ana", lines.sortedByStat(null).first().player.firstName)
        // A missing rating goes last.
        val oneUnrated = lines.map { if (it.player.id == "p7") it.copy(stats = it.stats.copy(rating = null)) else it }
        assertEquals("p7", oneUnrated.sortedByStat(MatchStatColumn.RATING).last().player.id)
    }

    @Test
    fun theFiltersPickGoalsSubstitutionsOrCards() {
        val events = chronologicalEvents(staff)
        fun ids(filter: EventFilter) = events.filter(filter::accepts).map { it.key }
        // A goal and its assist are one row; theirs counts too.
        assertEquals(listOf("g1", "g2", "g3"), ids(EventFilter.GOALS))
        assertEquals(listOf("so-si"), ids(EventFilter.SUBSTITUTIONS))
        assertEquals(listOf("y1"), ids(EventFilter.CARDS))
        assertEquals(5, ids(EventFilter.ALL).size)
    }

    @Test
    fun eventsReadInTheOrderTheyHappenedHalfByHalf() {
        assertEquals(listOf(1, 1, 2, 2, 2), chronologicalEvents(staff).map { it.period })
        assertEquals(listOf(12, 20, 30, 35, 44), chronologicalEvents(staff).map { it.minute })
    }

    @Test
    fun theTimelineHasKickoffTheSubstitutionAndTheNewFormation() {
        val entries = matchTimeline(
            staff.teamSize, staff.match.startingFormation, staff.callUps.map { it.callUp },
            staff.callUps.associate { it.player.id to it.player.position }, staff.events, staff.lineupChanges
        )
        assertEquals(listOf(TimelineChange.KICKOFF, TimelineChange.SUBSTITUTION, TimelineChange.FORMATION), entries.map { it.change })
    }

    @Test
    fun aParentsRecordIsJustTheGoals() {
        val parent = previewFinishedDetails(canSeeRecord = false)
        assertEquals(listOf(EventType.GOAL, EventType.GOAL, EventType.GOAL), chronologicalEvents(parent).map { (it as com.ricardofrango.squadify.presentation.ui.livematch.DisplayEvent.Simple).event.type })
        assertEquals(emptyList<MatchPlayerStats>(), parent.playerStats)
    }

    @Test
    fun theStaffGetEveryTabAndAnyoneElseGeneralAndEvents() {
        assertEquals(listOf(FinishedTab.GENERAL, FinishedTab.STATS, FinishedTab.EVENTS, FinishedTab.TIMELINE), finishedTabs(canSeeMatchRecord = true))
        assertEquals(listOf(FinishedTab.GENERAL, FinishedTab.EVENTS), finishedTabs(canSeeMatchRecord = false))
    }

    @Test
    fun aGameEnteredAfterwardsHasNoTimeline() {
        assertEquals(listOf(FinishedTab.GENERAL, FinishedTab.STATS, FinishedTab.EVENTS), finishedTabs(canSeeMatchRecord = true, hasTimeline = false))
    }
}
