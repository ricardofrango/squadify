package com.ricardofrango.squadify.coach.presentation.navigation

import com.ricardofrango.squadify.presentation.navigation.Screen
import com.ricardofrango.squadify.presentation.navigation.SignedInHome

/**
 * The coach app's home once signed in: always the list of the coach's age groups, which offers creating
 * one (setting up their own space the first time) or joining one with a code when it's empty.
 */
object CoachSignedInHome : SignedInHome {
    override suspend fun screen(): Screen = Screen.CoachHome
}
