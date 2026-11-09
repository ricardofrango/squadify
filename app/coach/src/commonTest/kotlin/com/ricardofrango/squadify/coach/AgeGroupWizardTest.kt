package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.coach.presentation.ui.agegroups.PickedAgeGroup
import com.ricardofrango.squadify.coach.presentation.ui.agegroups.clubHasYears
import com.ricardofrango.squadify.coach.presentation.ui.agegroups.defaultTeamSize
import com.ricardofrango.squadify.coach.presentation.ui.agegroups.seasonEndYear
import com.ricardofrango.squadify.coach.presentation.ui.agegroups.subName
import com.ricardofrango.squadify.coach.presentation.ui.agegroups.teamNames
import com.ricardofrango.squadify.coach.presentation.ui.agegroups.wizardYears
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.SportType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The age-group wizard's rules: the season, the "Sub N" names, the a-side sizes, and which years a club
// already has.
class AgeGroupWizardTest {
    @Test
    fun theSeasonRunsJulyToJune() {
        assertEquals(2027, seasonEndYear(LocalDate(2026, 7, 1)))
        assertEquals(2027, seasonEndYear(LocalDate(2027, 6, 30)))
        assertEquals(2026, seasonEndYear(LocalDate(2026, 6, 30)))
    }

    @Test
    fun subNIsTheAgeInTheSeasonsLastYear() {
        assertEquals("Sub 11", subName(2027, 2016))
        assertEquals("Sub 12", subName(2027, 2015))
        // Sub 5 to Sub 19, youngest first.
        val years = wizardYears(2027)
        assertEquals(2022, years.first())
        assertEquals(2008, years.last())
        assertEquals(15, years.size)
    }

    @Test
    fun theUsualASideSizeForTheAge() {
        assertEquals(5, defaultTeamSize(SportType.FOOTBALL, 7))
        assertEquals(7, defaultTeamSize(SportType.FOOTBALL, 11))
        assertEquals(9, defaultTeamSize(SportType.FOOTBALL, 13))
        assertEquals(11, defaultTeamSize(SportType.FOOTBALL, 15))
        assertEquals(5, defaultTeamSize(SportType.FUTSAL, 15))
    }

    @Test
    fun onlyTheSameClubsAgeGroupsThisSeasonTakeItsYears() {
        fun ageGroup(name: String, year: Int, season: String = "s27") =
            AgeGroup(id = name, clubId = "space", name = name, seasonId = season, startYear = year, endYear = year)
        val existing = listOf(ageGroup("Povoense - Sub 11", 2016), ageGroup("Povoense - Sub 12", 2015, season = "s26"))
        assertTrue(clubHasYears(existing, "s27", "povoense ", 2016, 2016))
        // Another club's, or last season's, don't.
        assertFalse(clubHasYears(existing, "s27", "CF Santa Iria", 2016, 2016))
        assertFalse(clubHasYears(existing, "s27", "Povoense", 2015, 2015))
        // A two-year futsal age group overlaps on either year.
        assertTrue(clubHasYears(existing, "s27", "Povoense", 2017, 2016))
    }

    @Test
    fun teamsAreLetteredAndFutsalJoinsTheYoungerYear() {
        assertEquals(listOf("A", "B", "C"), teamNames(3))
        assertEquals(2017, PickedAgeGroup(2016, 5, joinYounger = true).startYear)
        assertEquals(2016, PickedAgeGroup(2016, 5).startYear)
    }
}
