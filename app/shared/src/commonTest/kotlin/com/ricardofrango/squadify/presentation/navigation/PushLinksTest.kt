package com.ricardofrango.squadify.presentation.navigation

import com.ricardofrango.squadify.model.PushLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What tapping each kind of push opens, and what's under it (§56). */
class PushLinksTest {

    @Test
    fun aMatchPushOpensTheMatchInsideItsClub() {
        assertEquals(
            listOf(Screen.Dashboard, Screen.ClubDetails("c"), Screen.MatchDetails("m")),
            pushLinkBackStack(PushLink.match("c", "m"), onlyClubId = null)
        )
    }

    @Test
    fun aSingleClubUsersClubIsTheRoot() {
        assertEquals(
            listOf(Screen.ClubDetails("c", isRootClub = true), Screen.MatchDetails("m")),
            pushLinkBackStack(PushLink.match("c", "m"), onlyClubId = "c")
        )
    }

    @Test
    fun aPushForAnotherClubPutsMyClubsUnderIt() {
        // e.g. just added to a second club: the old single club can't be the root any more.
        assertEquals(
            listOf(Screen.Dashboard, Screen.ClubDetails("new")),
            pushLinkBackStack(PushLink.club("new"), onlyClubId = "old")
        )
    }

    @Test
    fun ageGroupAndTeamPushesOpenThem() {
        assertEquals(Screen.AgeGroupDetails("ag"), pushLinkBackStack(PushLink.ageGroup("c", "ag"), "c").last())
        assertEquals(Screen.TeamDetails("t"), pushLinkBackStack(PushLink.team("c", "t"), "c").last())
    }

    @Test
    fun aClubPushOpensTheClub() {
        assertEquals(listOf(Screen.ClubDetails("c", isRootClub = true)), pushLinkBackStack(PushLink.club("c"), "c"))
    }

    @Test
    fun theInboxHandsALinkOutOnce() {
        val inbox = PushLinkInbox()
        inbox.post(PushLink.club("c"))
        assertEquals(PushLink.club("c"), inbox.take())
        assertNull(inbox.take())
        assertNull(inbox.link.value)
    }
}
