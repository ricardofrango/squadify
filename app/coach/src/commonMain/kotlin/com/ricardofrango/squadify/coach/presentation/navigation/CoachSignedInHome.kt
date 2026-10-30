package com.ricardofrango.squadify.coach.presentation.navigation

import com.ricardofrango.squadify.coach.domain.GetSpaceUseCase
import com.ricardofrango.squadify.presentation.navigation.Screen
import com.ricardofrango.squadify.presentation.navigation.SignedInHome

/**
 * The coach app's home once signed in: the space, or its first setup when the coach has none yet. A
 * failed check opens the space's home, which shows its own error and retry, and goes to the setup from
 * there if it turns out there's no space.
 */
class CoachSignedInHome(private val getSpaceUseCase: GetSpaceUseCase) : SignedInHome {
    override suspend fun screen(): Screen = try {
        if (getSpaceUseCase() == null) Screen.SpaceSetup else Screen.CoachHome
    } catch (e: Exception) {
        Screen.CoachHome
    }
}
