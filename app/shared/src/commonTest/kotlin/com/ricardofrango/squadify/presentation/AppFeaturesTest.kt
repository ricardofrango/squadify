package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.domain.SeasonDateDefaults
import com.ricardofrango.squadify.domain.config.AppFeatures
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.AgeGroupDetails
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.SeasonType
import com.ricardofrango.squadify.presentation.ui.agegroupdetails.idleFor
import com.ricardofrango.squadify.presentation.ui.createagegroups.CreateAgeGroupsUiState
import com.ricardofrango.squadify.presentation.ui.createteams.CreateTeamsUiState
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// What the shared screens leave out in the coach app (its AppFeatures, TECHNICAL_DECISIONS §72): the season
// pickers, and staff from club members.
class AppFeaturesTest {
    private fun ageGroup(id: String, seasonId: String, year: Int) =
        AgeGroup(id = id, clubId = "c1", name = id, seasonId = seasonId, startYear = year, endYear = year)

    @Test
    fun withASeasonPickerTheSeasonsBirthYearsAreTaken() {
        val state = CreateAgeGroupsUiState.Idle(
            seasons = emptyList(),
            selectedSeasonId = "s1",
            ageGroups = listOf(ageGroup("a", "s1", 2015), ageGroup("b", "s0", 2014))
        )
        assertEquals(setOf(2015), state.usedBirthYears)
    }

    @Test
    fun withoutOneNoYearIsTakenAndTheServerMarksAClash() {
        // The automatic season's id isn't a real one, so the space's age groups can't be matched to it:
        // last season's years must stay free for this season.
        val season = SeasonDateDefaults.automaticSeason("c1", LocalDate(2026, 10, 30))
        val state = CreateAgeGroupsUiState.Idle(
            pickSeason = false,
            seasons = listOf(season),
            selectedSeasonId = season.id,
            ageGroups = listOf(ageGroup("a", "s1", 2015))
        )
        assertTrue(state.usedBirthYears.isEmpty())
        assertEquals("26/27", state.selectedSeason?.name)
    }

    @Test
    fun createTeamsWithoutASeasonPickerOffersTheAgeGroupsOfItsSeason() {
        val state = CreateTeamsUiState.Idle(
            pickSeason = false,
            selectedSeasonId = "s1",
            ageGroups = listOf(ageGroup("a", "s1", 2015), ageGroup("b", "s0", 2014)),
            selectedAgeGroupId = "a"
        )
        assertEquals(listOf("a"), state.ageGroupsForSeason.map { it.id })
    }

    private val clubs = AppFeatures(training = true, seasons = true, photos = true, clubMembers = true)
    private val coach = AppFeatures(training = false, seasons = false, photos = false, clubMembers = false)

    private fun details(canAddStaff: Boolean) = AgeGroupDetails(
        ageGroup = ageGroup("a", "s1", 2015),
        club = Club(id = "c1", name = "Fontelas", status = ClubStatus.ACTIVE, createdAt = kotlin.time.Instant.fromEpochMilliseconds(0), sport = com.ricardofrango.squadify.model.SportType.FOOTBALL),
        season = Season(id = "s1", clubId = "c1", name = "26/27", type = SeasonType.EUROPEAN_YEAR, startDate = LocalDate(2026, 7, 1), endDate = LocalDate(2027, 6, 30), active = true),
        players = emptyList(), staff = emptyList(), teams = emptyList(), upcomingMatches = emptyList(), opponents = emptyList(),
        canAddStaff = canAddStaff, canCreateTeam = true, canManageMatch = true, canAssignPlayersToAgeGroup = true
    )

    @Test
    fun theClubsAppAddsStaffFromItsMembers() {
        val state = details(canAddStaff = true).idleFor(clubs)
        assertTrue(state.details.canAddStaff)
        assertFalse(state.invitesStaff)
    }

    @Test
    fun theCoachAppsOwnerInvitesAndRemovesStaffInstead() {
        val owner = details(canAddStaff = true).idleFor(coach)
        assertFalse(owner.details.canAddStaff)
        assertTrue(owner.invitesStaff)

        val staff = details(canAddStaff = false).idleFor(coach)
        assertFalse(staff.details.canAddStaff || staff.invitesStaff)
    }

    @Test
    fun onlyAnAppThatTurnsItOnOffersAccountDeletion() {
        // The clubs app's features leave it out (its own rules come later); the coach app's turn it on.
        assertFalse(clubs.accountDeletion)
        assertTrue(coach.copy(accountDeletion = true).accountDeletion)
    }
}
