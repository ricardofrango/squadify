package com.ricardofrango.squadify.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The link a push carries survives the trip through FCM's string-only data map. */
class PushLinkTest {

    @Test
    fun everyKindOfLinkRoundTrips() {
        listOf(
            PushLink.club("c"),
            PushLink.ageGroup("c", "ag"),
            PushLink.team("c", "t"),
            PushLink.match("c", "m")
        ).forEach { assertEquals(it, PushLink.fromData(it.toData())) }
    }

    @Test
    fun onlyTheIdsThatAreSetAreSent() {
        assertEquals(mapOf(PushLink.KEY_CLUB_ID to "c", PushLink.KEY_MATCH_ID to "m"), PushLink.match("c", "m").toData())
    }

    @Test
    fun aPushWithoutAClubOpensNothing() {
        // A push from the Firebase console, or from a server older than links.
        assertNull(PushLink.fromData(emptyMap()))
        assertNull(PushLink.fromData(mapOf(PushLink.KEY_MATCH_ID to "m")))
        assertNull(PushLink.fromData(mapOf(PushLink.KEY_CLUB_ID to " ")))
    }

    @Test
    fun blankIdsAreIgnored() {
        assertEquals(PushLink.club("c"), PushLink.fromData(mapOf(PushLink.KEY_CLUB_ID to "c", PushLink.KEY_MATCH_ID to "")))
    }
}
