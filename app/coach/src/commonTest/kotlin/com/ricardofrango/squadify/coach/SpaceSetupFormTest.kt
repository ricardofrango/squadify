package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.coach.presentation.ui.spacesetup.SpaceSetupForm
import com.ricardofrango.squadify.coach.presentation.ui.spacesetup.seasonName
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.presentation.ui.createagegroups.AgeGroupSpanType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The coach app's first setup (REQUIREMENTS §5c): the space, its first age group and team, no season.
class SpaceSetupFormTest {
    // The 26/27 season.
    private val seasonEndYear = 2027

    @Test
    fun aNameAndABirthYearAreEnough() {
        val form = SpaceSetupForm(name = " Fontelas FC ").withEndYear(2015)
        assertTrue(form.isComplete)

        val request = form.toRequest(seasonEndYear)!!
        assertEquals("Fontelas FC", request.name)
        assertEquals(SportType.FOOTBALL, request.sport)
        // A blank age group name is "Sub N" from the birth year, and a blank team name the age group's.
        assertEquals("Sub 12", request.ageGroup.name)
        assertEquals(2015, request.ageGroup.startYear)
        assertEquals(2015, request.ageGroup.endYear)
        assertEquals(11, request.ageGroup.teamSize)
        assertEquals("Sub 12", request.teamName)
        // The team plays the age group's size.
        assertNull(request.teamSize)
    }

    @Test
    fun theNamesTypedAreKept() {
        val form = SpaceSetupForm(name = "Fontelas", ageGroupName = " Benjamins ", teamName = " Benjamins A ").withEndYear(2017)
        val request = form.toRequest(seasonEndYear)!!
        assertEquals("Benjamins", request.ageGroup.name)
        assertEquals("Benjamins A", request.teamName)
    }

    @Test
    fun theTeamNameDefaultsToTheAgeGroupsTypedName() {
        val form = SpaceSetupForm(name = "Fontelas", ageGroupName = "Benjamins").withEndYear(2017)
        assertEquals("Benjamins", form.defaultTeamName(seasonEndYear))
        assertEquals("Benjamins", form.toRequest(seasonEndYear)!!.teamName)
    }

    @Test
    fun withoutANameOrABirthYearThereIsNoRequest() {
        assertFalse(SpaceSetupForm(name = "  ").withEndYear(2015).isComplete)
        assertFalse(SpaceSetupForm(name = "Fontelas").isComplete)
        assertNull(SpaceSetupForm(name = "Fontelas").toRequest(seasonEndYear))
    }

    @Test
    fun twoBirthYearsNeedTheYoungerOneAndInTheRightOrder() {
        val twoYears = SpaceSetupForm(name = "Fontelas").withSpanType(AgeGroupSpanType.TWO_YEAR).withEndYear(2016)
        // Not until the younger year is picked too.
        assertFalse(twoYears.isComplete)

        val picked = twoYears.withStartYear(2017).toRequest(seasonEndYear)!!
        assertEquals(2017, picked.ageGroup.startYear)
        assertEquals(2016, picked.ageGroup.endYear)

        // The younger year before the older one isn't an age group.
        assertFalse(twoYears.withStartYear(2015).isComplete)
    }

    @Test
    fun backToOneYearUsesTheOlderYear() {
        val form = SpaceSetupForm(name = "Fontelas")
            .withSpanType(AgeGroupSpanType.TWO_YEAR).withEndYear(2016).withStartYear(2017)
            .withSpanType(AgeGroupSpanType.SINGLE_YEAR)
        assertEquals(2016, form.startYear)
        assertEquals(2016, form.endYear)
    }

    @Test
    fun anotherSportKeepsTheTeamSizeOnlyIfItPlaysIt() {
        val futsal = SpaceSetupForm(teamSize = 7).withSport(SportType.FUTSAL)
        assertEquals(5, futsal.teamSize)
        assertEquals(4, SpaceSetupForm(teamSize = 4).withSport(SportType.FUTSAL).teamSize)
        assertEquals(4, futsal.copy(teamSize = 4).withSport(SportType.FOOTBALL).teamSize)
    }

    @Test
    fun theSeasonIsNamedAsTheServerNamesIt() {
        assertEquals("26/27", seasonName(2027))
        assertEquals("99/00", seasonName(2000))
    }
}
