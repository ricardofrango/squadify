package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.presentation.ui.livematch.formation
import com.ricardofrango.squadify.presentation.ui.livematch.teamSize
import com.ricardofrango.squadify.presentation.ui.livematch.withTeamSize
import com.ricardofrango.squadify.presentation.ui.matchdetails.minimumCallUp
import com.ricardofrango.squadify.presentation.ui.matchdetails.previewFinishedDetails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// A 9-a-side team can play a 7-a-side match: the match's own count drives the call-up's minimum and
// the pitch, the team's applying when the match has none.
class MatchTeamSizeTest {
    private val ninesTeam = previewFinishedDetails(canSeeRecord = true).let { it.copy(team = it.team.copy(teamSize = 9), match = it.match.copy(formation = null)) }

    @Test
    fun theMatchsOwnCountDrivesTheCallUpAndThePitch() {
        val sevens = ninesTeam.withTeamSize(7)
        assertEquals(7, sevens.teamSize)
        assertEquals(7, sevens.minimumCallUp())
        assertEquals(6, sevens.formation().sum())
    }

    @Test
    fun withoutItsOwnCountTheMatchPlaysTheTeams() {
        assertEquals(9, ninesTeam.teamSize)
        assertEquals(9, ninesTeam.minimumCallUp())
        // Picking the team's own count back stores none, as the server does.
        assertNull(ninesTeam.withTeamSize(7).withTeamSize(9).match.teamSize)
    }
}
