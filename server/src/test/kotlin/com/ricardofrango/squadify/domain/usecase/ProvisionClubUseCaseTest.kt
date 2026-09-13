package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.InviteRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ProvisionClubRequest
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class ProvisionClubUseCaseTest {

    private val clubRepository = mockk<ClubRepository>()
    private val userRepository = mockk<UserRepository>()
    private val inviteRepository = mockk<InviteRepository>(relaxed = true)
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val useCase = ProvisionClubUseCase(
        clubRepository, userRepository, inviteRepository,
        CheckUserIsAdminUseCase(userRepository, transactionManager), transactionManager
    )

    private fun user(id: String, admin: Boolean) = User(
        id, "$id@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE,
        isPlatformAdmin = admin, createdAt = Instant.fromEpochMilliseconds(0)
    )

    @Test
    fun platformAdminProvisionsAClubWithTheRequestedSport() = runBlocking {
        coEvery { userRepository.findById("admin") } returns user("admin", admin = true)
        coEvery { userRepository.findByEmail("dir@t.com") } returns null
        val saved = slot<com.ricardofrango.squadify.model.Club>()
        coEvery { clubRepository.createClub(capture(saved)) } answers { saved.captured }

        val result = useCase("admin", ProvisionClubRequest("dir@t.com", "AD Futsal", SportType.FUTSAL))

        assertEquals(SportType.FUTSAL, saved.captured.sport)
        assertEquals(SportType.FUTSAL, result.sport)
    }

    @Test
    fun nonAdminIsDenied() = runBlocking {
        coEvery { userRepository.findById("member") } returns user("member", admin = false)

        assertFailsWith<ForbiddenException> {
            useCase("member", ProvisionClubRequest("dir@t.com", "X", SportType.FOOTBALL))
        }
        Unit
    }
}
