package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * A platform admin only counts as one while ACTIVE -- a suspended admin's still-valid token must not
 * keep reaching admin-only actions (see "issues to fix.md", "User status is never enforced": this was
 * the other still-open half alongside LoginUseCase). Returns `false` rather than throwing so every
 * non-throwing caller (GetClubsUseCase deciding "every club" vs "just mine") degrades gracefully;
 * callers that gate access on this result (GetSuperAdminDashboardUseCase, ProvisionClubUseCase)
 * already throw ForbiddenException when it's false.
 */
class CheckUserIsAdminUseCaseTest {

    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val useCase = CheckUserIsAdminUseCase(userRepository, transactionManager)

    private fun user(isPlatformAdmin: Boolean, status: UserStatus) = User(
        id = "user-1", email = "u@test.com", firstName = "U", lastName = "User",
        isPlatformAdmin = isPlatformAdmin, status = status, createdAt = Instant.fromEpochMilliseconds(0)
    )

    @Test
    fun activePlatformAdminIsTrue() = runBlocking {
        coEvery { userRepository.findById("user-1") } returns user(isPlatformAdmin = true, status = UserStatus.ACTIVE)

        assertEquals(true, useCase("user-1"))
    }

    @Test
    fun suspendedPlatformAdminIsFalse() = runBlocking {
        coEvery { userRepository.findById("user-1") } returns user(isPlatformAdmin = true, status = UserStatus.SUSPENDED)

        assertEquals(false, useCase("user-1"))
    }

    @Test
    fun activeNonAdminIsFalse() = runBlocking {
        coEvery { userRepository.findById("user-1") } returns user(isPlatformAdmin = false, status = UserStatus.ACTIVE)

        assertEquals(false, useCase("user-1"))
    }
}
