package com.ricardofrango.squadify.data.service

import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * RESEND_API_KEY is unset in this environment (and in CI), so ServerConfig.resendApiKey is blank --
 * exercises the real "must never throw" contract from EmailService's doc, not a mocked stand-in for it.
 */
class ResendEmailServiceTest {

    private val emailService = ResendEmailService()

    @Test
    fun sendClubInviteNeverThrowsWithNoApiKeyConfigured() = runBlocking {
        emailService.sendClubInvite(to = "someone@example.com", clubName = "Test FC")
    }

    @Test
    fun sendClubMembershipAddedNeverThrowsWithNoApiKeyConfigured() = runBlocking {
        emailService.sendClubMembershipAdded(to = "someone@example.com", firstName = "Alex", clubName = "Test FC")
    }

    @Test
    fun sendAgeGroupStaffAddedNeverThrowsWithNoApiKeyConfigured() = runBlocking {
        emailService.sendAgeGroupStaffAdded(
            to = "someone@example.com",
            firstName = "Alex",
            ageGroupName = "U12",
            clubName = "Test FC"
        )
    }

    @Test
    fun sendPasswordResetCodeNeverThrowsWithNoApiKeyConfigured() = runBlocking {
        emailService.sendPasswordResetCode(to = "someone@example.com", firstName = "Alex", code = "123456", validMinutes = 15)
    }
}
