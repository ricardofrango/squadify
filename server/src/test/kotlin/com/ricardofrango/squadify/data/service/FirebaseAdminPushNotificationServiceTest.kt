package com.ricardofrango.squadify.data.service

import com.ricardofrango.squadify.domain.service.GoalPush
import com.ricardofrango.squadify.model.PushLink
import com.ricardofrango.squadify.model.PushMessage
import com.ricardofrango.squadify.model.MatchScore
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

/**
 * FIREBASE_SERVICE_ACCOUNT_JSON is unset in this environment (and in CI), so
 * ServerConfig.firebaseServiceAccountJson is blank -- exercises the real "must never throw" contract
 * from PushNotificationService's doc, not a mocked stand-in for it.
 */
class FirebaseAdminPushNotificationServiceTest {

    private val pushNotificationService = FirebaseAdminPushNotificationService()
    private val link = PushLink.club("club-1")

    @Test
    fun sendClubMembershipAddedNeverThrowsWithNoCredentialConfigured() = runBlocking {
        pushNotificationService.sendClubMembershipAdded(link = link, tokens = listOf("some-device-token"), clubName = "Test FC")
    }

    @Test
    fun sendClubMembershipAddedNeverThrowsWithEmptyTokenList() = runBlocking {
        pushNotificationService.sendClubMembershipAdded(link = link, tokens = emptyList(), clubName = "Test FC")
    }

    @Test
    fun sendAgeGroupStaffAddedNeverThrowsWithNoCredentialConfigured() = runBlocking {
        pushNotificationService.sendAgeGroupStaffAdded(
            link = link,
            tokens = listOf("some-device-token"),
            ageGroupName = "U12",
            clubName = "Test FC"
        )
    }

    @Test
    fun sendAgeGroupStaffAddedNeverThrowsWithEmptyTokenList() = runBlocking {
        pushNotificationService.sendAgeGroupStaffAdded(link = link, tokens = emptyList(), ageGroupName = "U12", clubName = "Test FC")
    }

    private val score = MatchScore("m-1", 1, 0)

    @Test
    fun sendMatchScheduledNeverThrowsWithNoCredentialConfigured() = runBlocking {
        pushNotificationService.sendMatchScheduled(link = link, tokens = listOf("some-device-token"), teamName = "Sub-15 A", opponentName = "Rivals FC")
    }

    @Test
    fun sendMatchRescheduledNeverThrowsWithNoCredentialConfigured() = runBlocking {
        pushNotificationService.sendMatchRescheduled(link = link, tokens = listOf("some-device-token"), teamName = "Sub-15 A", opponentName = "Rivals FC")
    }

    @Test
    fun sendMatchCancelledNeverThrowsWithNoCredentialConfigured() = runBlocking {
        pushNotificationService.sendMatchCancelled(link = link, tokens = listOf("some-device-token"), teamName = "Sub-15 A", opponentName = "Rivals FC")
    }

    @Test
    fun sendMatchKickedOffNeverThrowsWithNoCredentialConfigured() = runBlocking {
        pushNotificationService.sendMatchKickedOff(link = link, tokens = listOf("some-device-token"), teamName = "Sub-15 A", opponentName = "Rivals FC")
    }

    @Test
    fun sendHalfTimeNeverThrowsWithNoCredentialConfigured() = runBlocking {
        pushNotificationService.sendHalfTime(link = link, tokens = listOf("some-device-token"), teamName = "Sub-15 A", opponentName = "Rivals FC", score = score)
    }

    @Test
    fun sendSecondHalfStartedNeverThrowsWithNoCredentialConfigured() = runBlocking {
        pushNotificationService.sendSecondHalfStarted(link = link, tokens = listOf("some-device-token"), teamName = "Sub-15 A", opponentName = "Rivals FC")
    }

    @Test
    fun sendMatchFinishedNeverThrowsWithNoCredentialConfigured() = runBlocking {
        pushNotificationService.sendMatchFinished(link = link, tokens = listOf("some-device-token"), teamName = "Sub-15 A", opponentName = "Rivals FC", score = score)
    }

    @Test
    fun sendGoalScoredNeverThrowsWithNoCredentialConfigured() = runBlocking {
        pushNotificationService.sendGoalScored(link = link, tokens = listOf("some-device-token"), teamName = "Sub-15 A", opponentName = "Rivals FC", score = score)
    }

    @Test
    fun sendGoalScoredNeverThrowsWithALargeTokenListSpanningMultipleChunks() = runBlocking {
        // Exercises the >500-token chunking path -- see FirebaseAdminPushNotificationService.send.
        pushNotificationService.sendGoalScored(
            link = link,
            tokens = (1..1200).map { "device-token-$it" },
            teamName = "Sub-15 A",
            opponentName = "Rivals FC",
            score = score
        )
    }

    @Test
    fun sendTeamCreatedNeverThrowsWithOneTeamName() = runBlocking {
        pushNotificationService.sendTeamCreated(
            link = link,
            tokens = listOf("some-device-token"),
            teamNames = listOf("Sub-15 A"),
            ageGroupName = "U15",
            clubName = "Test FC"
        )
    }

    @Test
    fun sendTeamCreatedNeverThrowsWithSeveralTeamNames() = runBlocking {
        pushNotificationService.sendTeamCreated(
            link = link,
            tokens = listOf("some-device-token"),
            teamNames = listOf("Sub-15 A", "Sub-15 B"),
            ageGroupName = "U15",
            clubName = "Test FC"
        )
    }

    // ---- What a match's notification shows: one thread per match, each line from someone ----

    private val matchLink = PushLink.match("club-1", "m-1")
    private val goalScore = MatchScore("m-1", 2, 1)

    @Test
    fun ourCreditedGoalIsALineFromTheScorerWithTheirPhoto() {
        val message = FirebaseAdminPushNotificationService.goalMessage(matchLink, "Sub-13 A", "Fontelas", goalScore, GoalPush(true, "Rodrigo Costa", "https://x/photo.png"))
        assertEquals(PushMessage.MatchThread("Sub-13 A", "Fontelas", PushMessage.Sender.SCORER, "Rodrigo Costa"), message.thread)
        assertEquals("https://x/photo.png", message.imageUrl)
        assertEquals("push_goal_scored" to listOf("Sub-13 A", "2", "1", "Fontelas"), message.bodyKey to message.bodyArgs)
    }

    @Test
    fun aGoalWithoutAScorerIsALineFromOurTeam() {
        val message = FirebaseAdminPushNotificationService.goalMessage(matchLink, "Sub-13 A", "Fontelas", goalScore, GoalPush())
        assertEquals(PushMessage.Sender.TEAM, message.thread!!.sender)
        assertEquals("Sub-13 A", message.thread!!.senderName)
    }

    @Test
    fun theirGoalIsALineFromTheOpponent() {
        val message = FirebaseAdminPushNotificationService.goalMessage(matchLink, "Sub-13 A", "Fontelas", goalScore, GoalPush(forUs = false, imageUrl = null))
        assertEquals(PushMessage.MatchThread("Sub-13 A", "Fontelas", PushMessage.Sender.OPPONENT, "Fontelas"), message.thread)
    }

    @Test
    fun kickoffHalfTimeAndFullTimeAreLinesFromOurTeam() {
        val message = FirebaseAdminPushNotificationService.matchMessage(matchLink, "live_matches", "push_match_finished", listOf("Sub-13 A", "2", "1", "Fontelas"), "Sub-13 A", "Fontelas")
        assertEquals(PushMessage.MatchThread("Sub-13 A", "Fontelas", PushMessage.Sender.TEAM, "Sub-13 A"), message.thread)
    }
}
