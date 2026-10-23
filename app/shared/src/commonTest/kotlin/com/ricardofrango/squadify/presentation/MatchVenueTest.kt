package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchType
import com.ricardofrango.squadify.model.MatchVenue
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.ourSideFirst
import com.ricardofrango.squadify.presentation.ui.components.homeFirst
import com.ricardofrango.squadify.presentation.ui.components.venueLocation
import com.ricardofrango.squadify.presentation.ui.creatematch.CreateMatchUiState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Home / away / neutral (the developer's rules, 2026-10-23): picking it prefills the location, a new
// match can't be scheduled without one, and the home side comes first -- our club on neutral ground.
class MatchVenueTest {
    private val opponent = Opponent("o1", "c1", "AD Fontelas", stadiumName = "Campo da Fontelas", stadiumAddress = "Rua do Campo 1, Fontelas")

    @Test
    fun theVenuePrefillsTheLocation() {
        assertEquals("Estádio Municipal", venueLocation(MatchVenue.HOME, "Estádio Municipal", opponent))
        assertEquals("Rua do Campo 1, Fontelas", venueLocation(MatchVenue.AWAY, "Estádio Municipal", opponent))
        assertEquals("", venueLocation(MatchVenue.NEUTRAL, "Estádio Municipal", opponent))
    }

    @Test
    fun awayFallsBackToTheOpponentsStadiumNameAndHomeToNothing() {
        assertEquals("Campo da Fontelas", venueLocation(MatchVenue.AWAY, null, opponent.copy(stadiumAddress = null)))
        assertEquals("", venueLocation(MatchVenue.HOME, null, opponent))
    }

    @Test
    fun theHomeSideComesFirstAndOurClubOnNeutralGround() {
        val score = MatchScore("m1", 2, 1) // we scored 2
        assertEquals("2–1", score.homeFirst(MatchVenue.HOME))
        assertEquals("1–2", score.homeFirst(MatchVenue.AWAY))
        assertEquals("2–1", score.homeFirst(MatchVenue.NEUTRAL))
        assertTrue(MatchVenue.NEUTRAL.ourSideFirst())
        assertFalse(MatchVenue.AWAY.ourSideFirst())
    }

    @Test
    fun aMatchCantBeScheduledWithoutAVenue() {
        val form = CreateMatchUiState.Idle(
            clubId = "c1", clubName = "CD Ribamar", ageGroupId = "ag1", ageGroupName = "Sub-15", seasonName = "26/27",
            teamFixed = true, teams = listOf(Team("t1", "c1", "ag1", "s1", "Sub-15 A", active = true)), selectedTeamId = "t1",
            opponents = listOf(opponent), selectedOpponentId = "o1", matchType = MatchType.CHAMPIONSHIP,
            date = LocalDate(2026, 10, 24), time = LocalTime(10, 30)
        )
        assertFalse(form.canSubmit)
        assertTrue(form.copy(venue = MatchVenue.AWAY).canSubmit)
    }
}
