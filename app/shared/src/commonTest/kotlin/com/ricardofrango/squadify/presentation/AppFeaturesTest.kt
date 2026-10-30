package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.domain.SeasonDateDefaults
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.presentation.ui.createagegroups.CreateAgeGroupsUiState
import com.ricardofrango.squadify.presentation.ui.createteams.CreateTeamsUiState
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The create screens without a season to pick (the coach app's AppFeatures, TECHNICAL_DECISIONS §72).
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
}
