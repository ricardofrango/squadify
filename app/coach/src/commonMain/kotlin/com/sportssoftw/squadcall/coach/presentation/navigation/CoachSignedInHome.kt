package com.sportssoftw.squadcall.coach.presentation.navigation

import com.sportssoftw.squadcall.model.PushLink
import com.sportssoftw.squadcall.presentation.navigation.Screen
import com.sportssoftw.squadcall.presentation.navigation.pushLinkTarget
import com.sportssoftw.squadcall.presentation.navigation.SignedInHome

/**
 * The coach app's home once signed in: always the coach's spaces, which offers creating their first age
 * groups (setting up their own space the first time) or joining someone's with a code.
 */
object CoachSignedInHome : SignedInHome {
    override suspend fun screen(): Screen = Screen.CoachHome

    /** A tapped notification: its match, team or age group, with its space's details and Home under it. */
    override fun pushLinkBackStack(link: PushLink, home: Screen): List<Screen> = coachPushLinkBackStack(link)
}

fun coachPushLinkBackStack(link: PushLink): List<Screen> =
    listOf(Screen.CoachHome, Screen.SpaceDetails(link.clubId)) + listOfNotNull(pushLinkTarget(link))
