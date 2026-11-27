package com.sportssoftw.squadcall.presentation.navigation

import com.sportssoftw.squadcall.model.PushLink
import com.sportssoftw.squadcall.domain.usecase.GetClubsUseCase
import com.sportssoftw.squadcall.model.Club
import com.sportssoftw.squadcall.presentation.ui.theme.ClubThemeStore

/**
 * The clubs app's home once signed in ([clubsHome]). The clubs' colours are kept too, so a screen
 * opened from a push notification wears its club's without My clubs or the club screen having shown.
 */
class ClubsSignedInHome(
    private val getClubsUseCase: GetClubsUseCase,
    private val clubThemeStore: ClubThemeStore
) : SignedInHome {
    override suspend fun screen(): Screen {
        val clubs = try {
            getClubsUseCase().also(clubThemeStore::remember)
        } catch (e: Exception) {
            null
        }
        return clubsHome(clubs)
    }

    // The club under the target, as before: a single-club user's club as the root, else My clubs too.
    override fun pushLinkBackStack(link: PushLink, home: Screen): List<Screen> =
        pushLinkBackStack(link, onlyClubId = (home as? Screen.ClubDetails)?.takeIf { it.isRootClub }?.clubId)
}

/**
 * A single-club user's club, as the root (no club list under it, so no back arrow); anyone else, or a
 * failed fetch ([clubs] null), My clubs, which has its own error and retry.
 */
fun clubsHome(clubs: List<Club>?): Screen =
    clubs?.singleOrNull()?.let { Screen.ClubDetails(it.id, isRootClub = true) } ?: Screen.Dashboard
