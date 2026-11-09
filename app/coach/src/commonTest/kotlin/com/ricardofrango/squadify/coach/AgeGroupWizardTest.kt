package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.coach.presentation.ui.agegroups.AddAgeGroupsUiState
import com.ricardofrango.squadify.coach.presentation.ui.agegroups.AgeGroupRow
import com.ricardofrango.squadify.coach.presentation.ui.agegroups.RowProblem
import com.ricardofrango.squadify.coach.presentation.ui.agegroups.clubHasYears
import com.ricardofrango.squadify.coach.presentation.ui.agegroups.seasonEndYear
import com.ricardofrango.squadify.coach.presentation.ui.agegroups.subName
import com.ricardofrango.squadify.coach.presentation.ui.agegroups.teamNames
import com.ricardofrango.squadify.coach.presentation.ui.agegroups.wizardYearRange
import com.ricardofrango.squadify.model.AgeGroup
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The age-group wizard's rules: the season, the "Sub N" names, which years a club already has, and what's
// wrong with each age group's box.
class AgeGroupWizardTest {
    private fun ageGroup(name: String, year: Int, season: String = "s27") =
        AgeGroup(id = name, clubId = "space", name = name, seasonId = season, startYear = year, endYear = year)

    @Test
    fun theSeasonRunsJulyToJune() {
        assertEquals(2027, seasonEndYear(LocalDate(2026, 7, 1)))
        assertEquals(2027, seasonEndYear(LocalDate(2027, 6, 30)))
        assertEquals(2026, seasonEndYear(LocalDate(2026, 6, 30)))
    }

    @Test
    fun subNIsTheAgeInTheSeasonsLastYear() {
        assertEquals("Sub 11", subName(2027, 2016))
        // Sub 5 to Sub 19.
        assertEquals(2008..2022, wizardYearRange(2027))
    }

    @Test
    fun onlyTheSameClubsAgeGroupsThisSeasonTakeItsYears() {
        val existing = listOf(ageGroup("Povoense - Sub 11", 2016), ageGroup("Povoense - Sub 12", 2015, season = "s26"))
        assertTrue(clubHasYears(existing, "s27", "povoense ", 2016, 2016))
        // Another club's, or last season's, don't.
        assertFalse(clubHasYears(existing, "s27", "CF Santa Iria", 2016, 2016))
        assertFalse(clubHasYears(existing, "s27", "Povoense", 2015, 2015))
        // A two-year futsal age group overlaps on either year.
        assertTrue(clubHasYears(existing, "s27", "Povoense", 2017, 2016))
    }

    @Test
    fun eachBoxNeedsAFreeYearInRangeAndASize() {
        val rows = listOf(
            AgeGroupRow(0, "", teamSize = 7),
            AgeGroupRow(1, "201", teamSize = 7),
            AgeGroupRow(2, "2030", teamSize = 7),
            AgeGroupRow(3, "2017", teamSize = 7),
            AgeGroupRow(4, "2017", teamSize = 7),
            AgeGroupRow(5, "2016", teamSize = 7),
            AgeGroupRow(6, "2015"),
            AgeGroupRow(7, "2014", teamSize = 9)
        )
        val state = AddAgeGroupsUiState(
            loading = false, seasonEndYear = 2027, seasonId = "s27", clubName = "Povoense",
            existing = listOf(ageGroup("Povoense - Sub 11", 2016)), rows = rows
        )
        assertEquals(
            listOf(
                RowProblem.YEAR_MISSING, RowProblem.YEAR_MISSING, RowProblem.YEAR_OUT_OF_RANGE, null,
                RowProblem.YEAR_REPEATED, RowProblem.YEAR_TAKEN, RowProblem.SIZE_MISSING, null
            ),
            rows.map { state.problemOf(it) }
        )
        assertFalse(state.rowsValid)
        assertNull(state.copy(rows = listOf(rows[3], rows[7])).let { s -> s.rows.map { s.problemOf(it) }.firstOrNull { it != null } })
    }

    @Test
    fun teamsAreLetteredAndFutsalJoinsTheYoungerYear() {
        assertEquals(listOf("A", "B", "C"), teamNames(3))
        assertEquals(2017, AgeGroupRow(0, "2016", joinYounger = true).startYear)
        assertEquals(2016, AgeGroupRow(0, "2016").startYear)
    }
}
