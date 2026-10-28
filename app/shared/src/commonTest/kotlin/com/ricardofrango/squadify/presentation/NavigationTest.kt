package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.presentation.navigation.AppNavigator
import com.ricardofrango.squadify.presentation.navigation.Screen
import com.ricardofrango.squadify.presentation.navigation.screenFromRoute
import com.ricardofrango.squadify.presentation.navigation.toRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigationTest {

    // ---- Routes: the web addresses Navigation 2 gave each screen, which shared links still use ----

    @Test
    fun routesKeepNavigation2sFormat() {
        assertEquals("splash", Screen.Splash.toRoute())
        assertEquals("team/t1", Screen.TeamDetails("t1").toRoute())
        assertEquals("club/c1?isRootClub=false", Screen.ClubDetails("c1").toRoute())
        assertEquals("match-report/m1?edit=true", Screen.MatchReport("m1", edit = true).toRoute())
        assertEquals("full-stats?teamId=t1&ageGroupId=null", Screen.FullStats(teamId = "t1").toRoute())
    }

    @Test
    fun routesEncodeTheirValues() {
        assertEquals("create-season/c1/Sporting%20%2F%20B%C3%A9lem", Screen.CreateSeason("c1", "Sporting / Bélem").toRoute())
    }

    @Test
    fun everyRouteReadsBackAsItsScreen() {
        val screens = listOf(
            Screen.Splash,
            Screen.Dashboard,
            Screen.ClubDetails("c1", isRootClub = true),
            Screen.CreateSeason("c1", "Sporting / Bélem"),
            Screen.ClubMemberDetails("c1", inviteId = "i1"),
            Screen.CreateAgeGroups("c1", "Club", "FOOTBALL"),
            Screen.FullStats(ageGroupId = "a1"),
            Screen.Opponents("c1", select = true, ageGroupId = "a1"),
            Screen.CreateOpponent("c1", name = "Benfica & Co"),
            Screen.PlayerDetails("p1", seasonId = "s1", statsFilter = "OFFICIAL")
        )
        screens.forEach { assertEquals(it, screenFromRoute(it.toRoute()), it.toRoute()) }
    }

    @Test
    fun aLinkMayLeaveOutArgumentsWithDefaults() {
        assertEquals(Screen.ClubDetails("c1"), screenFromRoute("club/c1"))
        assertEquals(Screen.MatchReport("m1"), screenFromRoute("#match-report/m1"))
    }

    @Test
    fun aRouteThatIsntAScreenIsNull() {
        assertNull(screenFromRoute("nowhere/1"))
        assertNull(screenFromRoute("team")) // its id is missing
        assertNull(screenFromRoute("team/t1/extra"))
        assertNull(screenFromRoute("match-report/m1?edit=maybe"))
    }

    // ---- The back stack: NavController's popUpTo and popBackStack ----

    private fun AppNavigator.screens() = backStack.map { it.screen }

    @Test
    fun navigatePushesAScreen() {
        val nav = AppNavigator.startingAt(Screen.Dashboard)
        nav.navigate(Screen.ClubDetails("c1"))
        assertEquals(listOf(Screen.Dashboard, Screen.ClubDetails("c1")), nav.screens())
    }

    @Test
    fun popUpToInclusiveReplacesTheScreen() {
        val nav = AppNavigator.startingAt(Screen.Splash)
        nav.navigate(Screen.Dashboard) { popUpTo(Screen.Splash) { inclusive = true } }
        assertEquals(listOf<Screen>(Screen.Dashboard), nav.screens())
    }

    @Test
    fun popUpToATypeKeepsItWhenNotInclusive() {
        val nav = AppNavigator.startingAt(Screen.Dashboard)
        nav.navigate(Screen.ClubDetails("c1"))
        nav.navigate(Screen.TeamDetails("t1"))
        nav.navigate(Screen.Account) { popUpTo<Screen.ClubDetails>() }
        assertEquals(listOf(Screen.Dashboard, Screen.ClubDetails("c1"), Screen.Account), nav.screens())
    }

    @Test
    fun popUpToAScreenNotInTheStackPopsNothing() {
        val nav = AppNavigator.startingAt(Screen.Dashboard)
        nav.navigate(Screen.Account) { popUpTo(Screen.Login) { inclusive = true } }
        assertEquals(listOf(Screen.Dashboard, Screen.Account), nav.screens())
    }

    @Test
    fun popUpToRootLeavesOnlyTheNewScreen() {
        val nav = AppNavigator.startingAt(Screen.Dashboard)
        nav.navigate(Screen.ClubDetails("c1"))
        nav.navigate(Screen.Login) { popUpToRoot() }
        assertEquals(listOf<Screen>(Screen.Login), nav.screens())
    }

    @Test
    fun theLastScreenIsNeverPopped() {
        val nav = AppNavigator.startingAt(Screen.Dashboard)
        assertFalse(nav.popBackStack())
        assertFalse(nav.popBackStack<Screen.Dashboard>(inclusive = true))
        assertEquals(listOf<Screen>(Screen.Dashboard), nav.screens())
    }

    @Test
    fun popBackStackToAType() {
        val nav = AppNavigator.startingAt(Screen.Dashboard)
        nav.navigate(Screen.ClubDetails("c1"))
        nav.navigate(Screen.TeamDetails("t1"))
        nav.navigate(Screen.EditTeam("t1"))
        assertTrue(nav.popBackStack<Screen.TeamDetails>(inclusive = true))
        assertEquals(listOf(Screen.Dashboard, Screen.ClubDetails("c1")), nav.screens())
        assertFalse(nav.popBackStack<Screen.MatchDetails>(inclusive = false))
    }

    @Test
    fun aResultIsLeftOnTheScreenUnder() {
        val nav = AppNavigator.startingAt(Screen.ClubDetails("c1"))
        val club = nav.currentBackStackEntry!!
        val flag = club.savedStateHandle.getStateFlow("changed", false)
        nav.navigate(Screen.CreateSeason("c1", "Club"))
        nav.previousBackStackEntry!!.savedStateHandle["changed"] = true
        nav.popBackStack()
        assertTrue(flag.value)
        assertTrue(nav.isAlive(club))
        // An entry of the same screen from a restored back stack is another entry.
        assertFalse(nav.isAlive(AppNavigator.startingAt(Screen.ClubDetails("c1")).backStack.first()))
    }

    @Test
    fun browserBackKeepsTheEntriesThatStayTheSame() {
        val nav = AppNavigator.startingAt(Screen.Dashboard)
        nav.navigate(Screen.ClubDetails("c1"))
        nav.navigate(Screen.TeamDetails("t1"))
        val club = nav.backStack[1]
        nav.restoreBackStack(listOf(Screen.Dashboard, Screen.ClubDetails("c1")))
        assertEquals(listOf(Screen.Dashboard, Screen.ClubDetails("c1")), nav.screens())
        assertTrue(nav.backStack[1] === club)
    }
}
