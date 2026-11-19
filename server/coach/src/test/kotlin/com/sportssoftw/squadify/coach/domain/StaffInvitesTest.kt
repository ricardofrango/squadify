package com.sportssoftw.squadify.coach.domain

import com.sportssoftw.squadify.coach.data.StaffInviteRepository
import com.sportssoftw.squadify.coach.data.StoredStaffInvite
import com.sportssoftw.squadify.domain.repository.AgeGroupRepository
import com.sportssoftw.squadify.domain.repository.ClubRepository
import com.sportssoftw.squadify.domain.service.TransactionManager
import com.sportssoftw.squadify.exception.ValidationException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class StaffInvitesTest {
    @Test
    fun codesAreSixUnambiguousCharacters() {
        repeat(200) {
            val code = InviteCodes.generate()
            assertEquals(6, code.length)
            assertTrue(code.none { it in "01IOL" }, code)
        }
    }

    @Test
    fun aCodeIsTypedAnyWayAndShownWithADash() {
        assertEquals("K7Q4MZ", InviteCodes.normalize(" k7q-4mz "))
        assertEquals("K7Q-4MZ", InviteCodes.display("K7Q4MZ"))
    }

    @Test
    fun anExpiredCodeIsRefused() {
        val invites = mockk<StaffInviteRepository>()
        val created = Instant.parse("2026-10-01T10:00:00Z")
        coEvery { invites.findByCode("K7Q4MZ") } returns
            StoredStaffInvite("i1", "K7Q4MZ", "ag1", "owner", created, created.plus(kotlin.time.Duration.parse("7d")), acceptedBy = null)
        val transactionManager = object : TransactionManager {
            override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
        }
        val eightDaysLater = object : Clock { override fun now() = Instant.parse("2026-10-09T10:00:00Z") }
        val accept = AcceptStaffInviteUseCase(invites, mockk<AgeGroupRepository>(), mockk<ClubRepository>(), transactionManager, mockk(), mockk(), mockk(), mockk(), eightDaysLater)

        assertFailsWith<ValidationException> { runBlocking { accept("someone", "K7Q-4MZ") } }
    }
}
