package com.ricardofrango.squadify.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.ricardofrango.squadify.model.PushLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * The link of the push notification the user just tapped, until it's been opened. The platform puts
 * it here (Android: MainActivity, from the notification's intent); Splash opens it after a cold start,
 * once the session is confirmed, and AppNavigation opens one that arrives while the app is running.
 * If the user isn't signed in, it waits here until they are. See TECHNICAL_DECISIONS.md §56.
 */
class PushLinkInbox {
    private val _link = MutableStateFlow<PushLink?>(null)
    val link: StateFlow<PushLink?> = _link.asStateFlow()

    fun post(link: PushLink) {
        _link.value = link
    }

    /** The waiting link, removed so it's only opened once. */
    fun take(): PushLink? = _link.getAndUpdate { null }
}

/**
 * The screens to open for [link], bottom first: the club under the target, so the club's screen is
 * one step back (and the target is shown as part of that club). A single-club user's club is the root
 * ([onlyClubId]); anyone else gets My clubs under it.
 *
 * - a match (fixtures, live moments, results) opens Match details;
 * - a team opens Team details;
 * - an age group (added as staff, several teams created) opens the age group;
 * - otherwise (added to a club) the club itself.
 */
fun pushLinkBackStack(link: PushLink, onlyClubId: String?): List<Screen> {
    val club = if (onlyClubId == link.clubId) {
        listOf(Screen.ClubDetails(link.clubId, isRootClub = true))
    } else {
        listOf(Screen.Dashboard, Screen.ClubDetails(link.clubId))
    }
    val target = link.matchId?.let(Screen::MatchDetails)
        ?: link.teamId?.let(Screen::TeamDetails)
        ?: link.ageGroupId?.let(Screen::AgeGroupDetails)
    return club + listOfNotNull(target)
}

/** Replaces the whole back stack with [screens], bottom first. */
fun NavController.openBackStack(screens: List<Screen>) {
    val root = screens.firstOrNull() ?: return
    navigate(root) { popUpTo(graph.id) { inclusive = true } }
    screens.drop(1).forEach { navigate(it) }
}

/** Splash and the sign-in screens: a push link waits until the user is past them. */
fun NavDestination.isBeforeSignIn(): Boolean =
    hasRoute<Screen.Splash>() || hasRoute<Screen.Login>() || hasRoute<Screen.Register>() || hasRoute<Screen.ForgotPassword>()
