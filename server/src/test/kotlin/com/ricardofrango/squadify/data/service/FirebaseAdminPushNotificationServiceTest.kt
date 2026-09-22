package com.ricardofrango.squadify.data.service

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
}
