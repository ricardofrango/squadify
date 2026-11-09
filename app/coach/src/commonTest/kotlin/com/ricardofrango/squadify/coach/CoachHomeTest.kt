package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.coach.presentation.navigation.CoachSignedInHome
import com.ricardofrango.squadify.coach.presentation.navigation.coachPushLinkBackStack
import com.ricardofrango.squadify.model.PushLink
import com.ricardofrango.squadify.coach.presentation.ui.home.CoachHomeUiState
import com.ricardofrango.squadify.coach.presentation.ui.home.SpaceClub
import com.ricardofrango.squadify.coach.presentation.ui.home.clubsOf
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.SpaceAgeGroups
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.presentation.navigation.Screen
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

// The coach app's home (REQUIREMENTS §5c): the coach's own space and the ones they're staff in, and where
// a tapped notification leads.
class CoachHomeTest {
    private fun space(id: String, name: String) =
        Club(id = id, name = name, status = ClubStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FOOTBALL)

    private fun ageGroup(id: String, spaceId: String, year: Int) =
        AgeGroup(id = id, clubId = spaceId, name = id, seasonId = "s", startYear = year, endYear = year)

    private val mine = space("mine", "Fontelas FC")
    private val anas = space("anas", "Ana's team")

    private fun named(id: String, spaceId: String, name: String, year: Int, logoUrl: String? = null) =
        AgeGroup(id = id, clubId = spaceId, name = name, seasonId = "s", startYear = year, endYear = year, logoUrl = logoUrl)

    @Test
    fun theOwnSpaceApartFromTheOnesTheyreStaffIn() {
        val state = CoachHomeUiState.Loaded.of(
            listOf(
                SpaceAgeGroups(anas, isOwner = false, plan = SpacePlan("FREE"), ageGroups = listOf(ageGroup("ana-sub9", "anas", 2018))),
                SpaceAgeGroups(mine, isOwner = true, plan = SpacePlan("FREE"), ageGroups = listOf(ageGroup("sub13", "mine", 2014)))
            )
        )
        assertEquals(mine, state.ownSpace?.space)
        assertEquals(listOf(anas), state.otherSpaces.map { it.space })
    }

    @Test
    fun staffOnlyHaveNoSpaceOfTheirOwnYet() {
        val state = CoachHomeUiState.Loaded.of(listOf(SpaceAgeGroups(anas, isOwner = false, plan = SpacePlan("FREE"), ageGroups = listOf(ageGroup("ana-sub9", "anas", 2018)))), userName = "Rui Costa")
        assertNull(state.ownSpace)
        assertEquals("Rui Costa", state.userName)
        assertEquals(1, state.otherSpaces.size)
    }

    @Test
    fun aNewAccountHasNoSpaces() {
        val state = CoachHomeUiState.Loaded.of(emptyList())
        assertNull(state.ownSpace)
        assertTrue(state.otherSpaces.isEmpty())
    }

    @Test
    fun aSpacesClubsAreNamedOnceWithTheFirstCrestFound() {
        val space = SpaceAgeGroups(
            mine, isOwner = true, plan = SpacePlan("PRO"),
            ageGroups = listOf(
                named("a", "mine", "Povoense - Sub 12", 2015),
                named("b", "mine", "CF Santa Iria - Sub 9", 2018, logoUrl = "/age-groups/b/logo/1.png"),
                named("c", "mine", "povoense - Sub 13", 2014, logoUrl = "/age-groups/c/logo/2.png"),
                // Not named after a club: left out.
                named("d", "mine", "Sub 10", 2017)
            )
        )
        assertEquals(
            listOf(SpaceClub("CF Santa Iria", "/age-groups/b/logo/1.png"), SpaceClub("Povoense", "/age-groups/c/logo/2.png")),
            clubsOf(space)
        )
    }

    @Test
    fun signingInAlwaysOpensTheHome() = runTest {
        assertEquals(Screen.CoachHome, CoachSignedInHome.screen())
    }

    @Test
    fun aTappedNotificationOpensItsTargetOverItsSpace() {
        val home = listOf(Screen.CoachHome, Screen.SpaceDetails("s"))
        assertEquals(home + Screen.MatchDetails("m"), coachPushLinkBackStack(PushLink.match("s", "m")))
        assertEquals(home + Screen.TeamDetails("t"), coachPushLinkBackStack(PushLink.team("s", "t")))
        assertEquals(home + Screen.AgeGroupDetails("a"), coachPushLinkBackStack(PushLink.ageGroup("s", "a")))
        // A link to a space alone (never sent by the coach server): its details.
        assertEquals(home, coachPushLinkBackStack(PushLink.club("s")))
    }
}
