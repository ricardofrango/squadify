package com.ricardofrango.squadify.presentation.navigation

import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.SportType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

// Where the clubs app opens once signed in, from Splash or Login.
class ClubsSignedInHomeTest {
    private fun club(id: String) = Club(id = id, name = id, status = ClubStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FOOTBALL)

    @Test
    fun aSingleClubOpensAsTheRoot() {
        assertEquals(Screen.ClubDetails("c1", isRootClub = true), clubsHome(listOf(club("c1"))))
    }

    @Test
    fun severalClubsOrNoneOpenMyClubs() {
        assertEquals(Screen.Dashboard, clubsHome(listOf(club("c1"), club("c2"))))
        assertEquals(Screen.Dashboard, clubsHome(emptyList()))
    }

    @Test
    fun aFailedFetchOpensMyClubs() {
        assertEquals(Screen.Dashboard, clubsHome(null))
    }
}
