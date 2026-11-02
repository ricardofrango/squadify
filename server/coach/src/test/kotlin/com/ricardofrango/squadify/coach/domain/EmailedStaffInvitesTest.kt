package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.coach.data.StaffInviteRepository
import com.ricardofrango.squadify.coach.data.StoredStaffInvite
import com.ricardofrango.squadify.coach.data.staffInviteText
import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.CreateStaffInviteRequest
import com.ricardofrango.squadify.model.User
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

// An invite code sent by email: the address is checked and stored with the code, the email says who
// invites to what in the owner's language, an owner can email a limited number a day, and a code whose
// email didn't go is withdrawn.
class EmailedStaffInvitesTest {
    private val now = Instant.parse("2026-11-02T10:00:00Z")
    private val invites = mockk<StaffInviteRepository>(relaxed = true)
    private val mailer = mockk<StaffInviteMailer>()
    private val stored = slot<StoredStaffInvite>()
    private val letter = slot<StaffInviteEmail>()

    private fun createInvite(dailyEmails: Int = 20): CreateStaffInviteUseCase {
        val ageGroups = mockk<AgeGroupRepository>()
        coEvery { ageGroups.getAgeGroupById("ag1") } returns mockk<AgeGroup> {
            every { id } returns "ag1"
            every { clubId } returns "space1"
            every { name } returns "Sub-12"
        }
        val clubs = mockk<ClubRepository>()
        coEvery { clubs.getClubById("space1") } returns mockk<Club> { every { name } returns "Ana's team" }
        val users = mockk<UserRepository>()
        coEvery { users.findById("owner") } returns mockk<User> {
            every { firstName } returns "Ana"
            every { lastName } returns "Silva"
        }
        coEvery { invites.findByCode(any()) } returns null
        coEvery { invites.create(capture(stored)) } returns Unit
        val transactionManager = object : TransactionManager {
            override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
        }
        val clock = object : Clock { override fun now() = now }
        return CreateStaffInviteUseCase(
            invites, ageGroups, mockk<AccessPolicy>(relaxed = true), transactionManager, users, clubs, mailer, mockk<CoachPlans>(relaxed = true), clock, dailyEmails = dailyEmails
        )
    }

    @Test
    fun anEmailedCodeIsSentAndShownWithTheAddress() = runBlocking {
        coEvery { invites.countEmailedSince("owner", any()) } returns 0
        coEvery { mailer.sendStaffInvite(any(), capture(letter)) } returns true

        val invite = createInvite()("owner", "ag1", CreateStaffInviteRequest(" Joao@Example.com ", "pt"))

        assertEquals("joao@example.com", invite.email)
        assertEquals("joao@example.com", stored.captured.sentTo)
        coVerify { mailer.sendStaffInvite("joao@example.com", any()) }
        assertEquals(StaffInviteEmail("Ana Silva", "Sub-12", "Ana's team", invite.code, invite.expiresAt, "pt"), letter.captured)
    }

    @Test
    fun aCodeToShareByHandSendsNothing() = runBlocking {
        val invite = createInvite()("owner", "ag1")

        assertNull(invite.email)
        assertNull(stored.captured.sentTo)
        coVerify(exactly = 0) { mailer.sendStaffInvite(any(), any()) }
    }

    @Test
    fun anInvalidAddressIsRefusedBeforeAnyCode() {
        assertFailsWith<ValidationException> { runBlocking { createInvite()("owner", "ag1", CreateStaffInviteRequest("not-an-email")) } }
        coVerify(exactly = 0) { invites.create(any()) }
    }

    @Test
    fun theDailyLimitStopsEmailingButNotSharing() = runBlocking {
        coEvery { invites.countEmailedSince("owner", any()) } returns 2

        assertFailsWith<ValidationException> { createInvite(dailyEmails = 2)("owner", "ag1", CreateStaffInviteRequest("joao@example.com")) }
        coVerify(exactly = 0) { invites.create(any()) }
        createInvite(dailyEmails = 2)("owner", "ag1")
        coVerify(exactly = 1) { invites.create(any()) }
    }

    @Test
    fun aCodeWhoseEmailDidntGoIsWithdrawn() {
        coEvery { invites.countEmailedSince("owner", any()) } returns 0
        coEvery { mailer.sendStaffInvite(any(), any()) } returns false

        assertFailsWith<ValidationException> { runBlocking { createInvite()("owner", "ag1", CreateStaffInviteRequest("joao@example.com")) } }
        coVerify { invites.revoke("ag1", stored.captured.code) }
    }

    @Test
    fun theEmailIsInTheOwnersLanguageWithNamesEscaped() {
        val email = StaffInviteEmail("Ana <b>", "Sub-12", "Ana & Co", "K7Q-4MZ", Instant.parse("2026-11-09T10:00:00Z"), "pt")

        val (ptSubject, ptBody) = staffInviteText(email)
        assertEquals("Ana <b> convidou-te para o Sub-12 no Squadify Coach", ptSubject)
        assertTrue("Ana &lt;b&gt;" in ptBody && "Ana &amp; Co" in ptBody && "K7Q-4MZ" in ptBody && "9 de novembro" in ptBody)
        assertFalse("Ana <b>" in ptBody)

        val (enSubject, enBody) = staffInviteText(email.copy(language = "en"))
        assertEquals("Ana <b> invited you to Sub-12 on Squadify Coach", enSubject)
        assertTrue("9 November" in enBody && "Join with a code" in enBody)
        assertTrue("Your code" in staffInviteText(email.copy(language = null)).second)
    }
}
