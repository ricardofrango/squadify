package com.ricardofrango.squadify.data.service

import com.ricardofrango.squadify.model.MatchScore
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * FIREBASE_SERVICE_ACCOUNT_JSON is unset in this environment (and in CI), so
 * ServerConfig.firebaseServiceAccountJson is blank -- exercises the real "must never throw" contract
 * from PushNotificationService's doc, not a mocked stand-in for it.
 */
class FirebaseAdminPushNotificationServiceTest {

    private val pushNotificationService = FirebaseAdminPushNotificationService()

    @Test
    fun sendClubMembershipAddedNeverThrowsWithNoCredentialConfigured() = runBlocking {
        pushNotificationService.sendClubMembershipAdded(tokens = listOf("some-device-token"), clubName = "Test FC")
    }

    @Test
    fun sendClubMembershipAddedNeverThrowsWithEmptyTokenList() = runBlocking {
        pushNotificationService.sendClubMembershipAdded(tokens = emptyList(), clubName = "Test FC")
    }

    @Test
    fun sendAgeGroupStaffAddedNeverThrowsWithNoCredentialConfigured() = runBlocking {
        pushNotificationService.sendAgeGroupStaffAdded(
            tokens = listOf("some-device-token"),
            ageGroupName = "U12",
            clubName = "Test FC"
        )
    }

    @Test
    fun sendAgeGroupStaffAddedNeverThrowsWithEmptyTokenList() = runBlocking {
        pushNotificationService.sendAgeGroupStaffAdded(tokens = emptyList(), ageGroupName = "U12", clubName = "Test FC")
    }

    private val score = MatchScore("m-1", 1, 0)

    @Test
    fun sendMatchKickedOffNeverThrowsWithNoCredentialConfigured() = runBlocking {
        pushNotificationService.sendMatchKickedOff(tokens = listOf("some-device-token"), teamName = "Sub-15 A", opponentName = "Rivals FC")
    }

    @Test
    fun sendHalfTimeNeverThrowsWithNoCredentialConfigured() = runBlocking {
        pushNotificationService.sendHalfTime(tokens = listOf("some-device-token"), teamName = "Sub-15 A", opponentName = "Rivals FC", score = score)
    }

    @Test
    fun sendSecondHalfStartedNeverThrowsWithNoCredentialConfigured() = runBlocking {
        pushNotificationService.sendSecondHalfStarted(tokens = listOf("some-device-token"), teamName = "Sub-15 A", opponentName = "Rivals FC")
    }

    @Test
    fun sendMatchFinishedNeverThrowsWithNoCredentialConfigured() = runBlocking {
        pushNotificationService.sendMatchFinished(tokens = listOf("some-device-token"), teamName = "Sub-15 A", opponentName = "Rivals FC", score = score)
    }

    @Test
    fun sendGoalScoredNeverThrowsWithNoCredentialConfigured() = runBlocking {
        pushNotificationService.sendGoalScored(tokens = listOf("some-device-token"), teamName = "Sub-15 A", opponentName = "Rivals FC", score = score)
    }

    @Test
    fun sendGoalScoredNeverThrowsWithALargeTokenListSpanningMultipleChunks() = runBlocking {
        // Exercises the >500-token chunking path -- see FirebaseAdminPushNotificationService.send.
        pushNotificationService.sendGoalScored(
            tokens = (1..1200).map { "device-token-$it" },
            teamName = "Sub-15 A",
            opponentName = "Rivals FC",
            score = score
        )
    }
}
