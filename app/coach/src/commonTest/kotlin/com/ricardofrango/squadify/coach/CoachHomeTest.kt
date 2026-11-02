package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.coach.presentation.navigation.CoachSignedInHome
import com.ricardofrango.squadify.coach.presentation.navigation.coachPushLinkBackStack
import com.ricardofrango.squadify.model.PushLink
import com.ricardofrango.squadify.coach.presentation.ui.home.CoachHomeUiState
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.SpaceAgeGroups
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.presentation.navigation.Screen
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

// The coach app's home (REQUIREMENTS §5c): one list of the age groups of every space the coach is in, and
// where Create age group leads.
class CoachHomeTest {
    private fun space(id: String, name: String) =
        Club(id = id, name = name, status = ClubStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FOOTBALL)

    private fun ageGroup(id: String, spaceId: String, year: Int) =
        AgeGroup(id = id, clubId = spaceId, name = id, seasonId = "s", startYear = year, endYear = year)

    private val mine = space("mine", "Fontelas FC")
    private val anas = space("anas", "Ana's team")

    @Test
    fun theOwnSpacesAgeGroupsComeFirstEachYoungestFirst() {
        val state = CoachHomeUiState.Loaded.of(
            listOf(
                SpaceAgeGroups(anas, isOwner = false, ageGroups = listOf(ageGroup("ana-sub9", "anas", 2018))),
                SpaceAgeGroups(mine, isOwner = true, ageGroups = listOf(ageGroup("sub13", "mine", 2014), ageGroup("sub11", "mine", 2016)))
            )
        )
        assertEquals(listOf("sub11", "sub13", "ana-sub9"), state.ageGroups.map { it.ageGroup.id })
        assertEquals(listOf("Fontelas FC", "Fontelas FC", "Ana's team"), state.ageGroups.map { it.spaceName })
        // Each row has its space, for the logo before it.
        assertEquals(listOf(mine, mine, anas), state.ageGroups.map { it.space })
        assertEquals(mine, state.ownSpace)
    }

    @Test
    fun staffOnlyHaveNoSpaceToAddTo() {
        // Create age group then sets up their own space first.
        val state = CoachHomeUiState.Loaded.of(listOf(SpaceAgeGroups(anas, isOwner = false, ageGroups = listOf(ageGroup("ana-sub9", "anas", 2018)))))
        assertNull(state.ownSpace)
        assertEquals(1, state.ageGroups.size)
    }

    @Test
    fun aNewAccountHasAnEmptyList() {
        val state = CoachHomeUiState.Loaded.of(emptyList())
        assertTrue(state.ageGroups.isEmpty())
        assertNull(state.ownSpace)
    }

    @Test
    fun anOwnSpaceWithNoAgeGroupsLeftIsStillWhereNewOnesGo() {
        val state = CoachHomeUiState.Loaded.of(listOf(SpaceAgeGroups(mine, isOwner = true, ageGroups = emptyList())))
        assertTrue(state.ageGroups.isEmpty())
        assertEquals(mine, state.ownSpace)
    }

    @Test
    fun signingInAlwaysOpensTheHome() = runTest {
        assertEquals(Screen.CoachHome, CoachSignedInHome.screen())
    }

    @Test
    fun aTappedNotificationOpensItsTargetOverMyAgeGroups() {
        assertEquals(listOf(Screen.CoachHome, Screen.MatchDetails("m")), coachPushLinkBackStack(PushLink.match("s", "m")))
        assertEquals(listOf(Screen.CoachHome, Screen.TeamDetails("t")), coachPushLinkBackStack(PushLink.team("s", "t")))
        assertEquals(listOf(Screen.CoachHome, Screen.AgeGroupDetails("a")), coachPushLinkBackStack(PushLink.ageGroup("s", "a")))
        // A link to a space alone (never sent by the coach server): just the home.
        assertEquals(listOf(Screen.CoachHome), coachPushLinkBackStack(PushLink.club("s")))
    }
}
