package com.ricardofrango.squadify.coach.presentation.navigation

import com.ricardofrango.squadify.model.PushLink
import com.ricardofrango.squadify.presentation.navigation.Screen
import com.ricardofrango.squadify.presentation.navigation.pushLinkTarget
import com.ricardofrango.squadify.presentation.navigation.SignedInHome

/**
 * The coach app's home once signed in: always the list of the coach's age groups, which offers creating
 * one (setting up their own space the first time) or joining one with a code when it's empty.
 */
object CoachSignedInHome : SignedInHome {
    override suspend fun screen(): Screen = Screen.CoachHome

    /** A tapped notification: its match, team or age group, with My age groups under it. */
    override fun pushLinkBackStack(link: PushLink, home: Screen): List<Screen> = coachPushLinkBackStack(link)
}

fun coachPushLinkBackStack(link: PushLink): List<Screen> = listOf(Screen.CoachHome) + listOfNotNull(pushLinkTarget(link))
