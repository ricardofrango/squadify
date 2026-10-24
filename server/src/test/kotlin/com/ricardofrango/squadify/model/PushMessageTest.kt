package com.ricardofrango.squadify.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The data-only push format the server writes and the app reads back. */
class PushMessageTest {

    private val link = PushLink.match("club-1", "m-1")

    @Test
    fun aMatchLineRoundTrips() {
        val message = PushMessage(
            link, "live_matches", "push_goal_scored", listOf("Sub-13 A", "2", "1", "Fontelas"),
            imageUrl = "https://api.example.com/v1/public/players/p/photo/x.png?exp=1&sig=s",
            thread = PushMessage.MatchThread("Sub-13 A", "Fontelas", PushMessage.Sender.SCORER, "Rodrigo Costa")
        )
        assertEquals(message, PushMessage.fromData(message.toData()))
    }

    @Test
    fun aPlainPushRoundTripsWithoutAThread() {
        val message = PushMessage(PushLink.club("club-1"), "invites", "push_club_membership_added", listOf("Test FC"))
        assertEquals(message, PushMessage.fromData(message.toData()))
    }

    @Test
    fun emptyArgumentsAndOrderSurvive() {
        val message = PushMessage(link, "live_matches", "push_half_time", listOf("A", "", "0", "B"))
        assertEquals(listOf("A", "", "0", "B"), PushMessage.fromData(message.toData())!!.bodyArgs)
    }

    @Test
    fun notOursWithoutALinkOrText() {
        assertNull(PushMessage.fromData(mapOf(PushMessage.KEY_BODY to "push_goal_scored")))
        assertNull(PushMessage.fromData(link.toData()))
    }
}
