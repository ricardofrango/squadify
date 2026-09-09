package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.UserRepository
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.model.User
import com.ricardofrango.clubmanager.model.UserStatus
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mindrot.jbcrypt.BCrypt
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Business rule: on startup the server must guarantee a platform-admin account exists so a
 * freshly-wiped dev database is immediately usable, without ever mutating an existing account or
 * running when disabled. Mirrors the mockk + `runBlocking` style of `CreateTeamUseCaseTest`.
 */
class SeedPlatformAdminUseCaseTest {

    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }

    private val adminEmail = "admin@local.dev"
    private val adminPassword = "admin1234"

    private fun useCase(
        enabled: Boolean = true,
        email: String = adminEmail,
        password: String = adminPassword,
    ) = SeedPlatformAdminUseCase(userRepository, transactionManager, enabled, email, password)

    private fun existingUser() = User(
        id = "existing-id",
        email = adminEmail,
        firstName = "Someone",
        lastName = "Else",
        status = UserStatus.ACTIVE,
        isPlatformAdmin = false,
        createdAt = Instant.fromEpochMilliseconds(0),
    )

    @Test
    fun createsPlatformAdminWhenNoneExists() = runBlocking {
        coEvery { userRepository.findByEmail(adminEmail) } returns null
        val userSlot: CapturingSlot<User> = slot()
        val hashSlot: CapturingSlot<String> = slot()
        coEvery { userRepository.createUser(capture(userSlot), capture(hashSlot)) } answers { firstArg() }

        useCase().invoke()

        coVerify(exactly = 1) { userRepository.createUser(any(), any()) }
        assertEquals(adminEmail, userSlot.captured.email)
        assertTrue(userSlot.captured.isPlatformAdmin, "seeded account must be a platform admin")
        assertEquals(UserStatus.ACTIVE, userSlot.captured.status)
        assertTrue(BCrypt.checkpw(adminPassword, hashSlot.captured), "password must be stored BCrypt-hashed")
    }

    @Test
    fun doesNothingWhenAdminAlreadyExists() = runBlocking {
        coEvery { userRepository.findByEmail(adminEmail) } returns existingUser()

        useCase().invoke()

        coVerify(exactly = 0) { userRepository.createUser(any(), any()) }
        Unit
    }

    @Test
    fun doesNothingWhenSeedingIsDisabled() = runBlocking {
        useCase(enabled = false).invoke()

        coVerify(exactly = 0) { userRepository.findByEmail(any()) }
        coVerify(exactly = 0) { userRepository.createUser(any(), any()) }
        Unit
    }

    @Test
    fun doesNothingWhenCredentialsAreBlank() = runBlocking {
        useCase(email = "", password = "").invoke()

        coVerify(exactly = 0) { userRepository.findByEmail(any()) }
        coVerify(exactly = 0) { userRepository.createUser(any(), any()) }
        Unit
    }
}
