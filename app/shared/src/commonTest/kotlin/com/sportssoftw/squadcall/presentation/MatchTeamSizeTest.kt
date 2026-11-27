package com.sportssoftw.squadcall.presentation

import com.sportssoftw.squadcall.presentation.ui.livematch.formation
import com.sportssoftw.squadcall.presentation.ui.livematch.teamSize
import com.sportssoftw.squadcall.presentation.ui.livematch.withTeamSize
import com.sportssoftw.squadcall.presentation.ui.matchdetails.minimumCallUp
import com.sportssoftw.squadcall.presentation.ui.matchdetails.previewFinishedDetails
import kotlin.test.Test
import kotlin.test.assertEquals

// A 9-a-side team can play a 7-a-side match: the match's own count drives the call-up's minimum and
// the pitch, whatever its team plays now.
class MatchTeamSizeTest {
    private val ninesTeam = previewFinishedDetails(canSeeRecord = true).let {
        it.copy(team = it.team.copy(teamSize = 9), match = it.match.copy(formation = null, teamSize = 9))
    }

    @Test
    fun theMatchsCountDrivesTheCallUpAndThePitch() {
        val sevens = ninesTeam.withTeamSize(7)
        assertEquals(7, sevens.teamSize)
        assertEquals(7, sevens.minimumCallUp())
        assertEquals(6, sevens.formation().sum())
    }

    @Test
    fun aMatchKeepsItsCountWhenItsTeamsChanges() {
        val teamNowSevens = ninesTeam.copy(team = ninesTeam.team.copy(teamSize = 7))
        assertEquals(9, teamNowSevens.teamSize)
        assertEquals(9, teamNowSevens.minimumCallUp())
    }
}
