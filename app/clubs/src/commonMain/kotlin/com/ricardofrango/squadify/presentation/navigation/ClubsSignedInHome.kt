package com.ricardofrango.squadify.presentation.navigation

import com.ricardofrango.squadify.domain.usecase.GetClubsUseCase
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.presentation.ui.theme.ClubThemeStore

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
}

/**
 * A single-club user's club, as the root (no club list under it, so no back arrow); anyone else, or a
 * failed fetch ([clubs] null), My clubs, which has its own error and retry.
 */
fun clubsHome(clubs: List<Club>?): Screen =
    clubs?.singleOrNull()?.let { Screen.ClubDetails(it.id, isRootClub = true) } ?: Screen.Dashboard
