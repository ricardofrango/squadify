package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.UnauthorizedException
import com.ricardofrango.squadify.model.LoginRequest
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.time.Instant
import org.mindrot.jbcrypt.BCrypt

/**
 * Login must reject a non-ACTIVE account (SUSPENDED/INACTIVE/PENDING) even with the correct
 * password -- previously nothing checked `user.status` here, so a suspended user could still log in
 * and mint a fresh valid token (see "issues to fix.md", "User status is never enforced"). The status
 * check runs after password verification so a caller who doesn't already know the password can't use
 * it to probe whether an account exists or is suspended.
 */
class LoginUseCaseTest {

    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val useCase = LoginUseCase(userRepository, transactionManager)

    private val password = "correct-horse-battery-staple"
    private val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt())

    private fun user(status: UserStatus) = User(
        id = "user-1", email = "p@test.com", firstName = "P", lastName = "User",
        status = status, createdAt = Instant.fromEpochMilliseconds(0)
    )

    @Test
    fun activeUserWithCorrectPasswordLogsIn() = runBlocking {
        coEvery { userRepository.findByEmail("p@test.com") } returns user(UserStatus.ACTIVE)
        coEvery { userRepository.findPasswordHashByEmail("p@test.com") } returns passwordHash
        coEvery { userRepository.saveRefreshToken(any(), any(), any()) } returns Unit

        val response = useCase(LoginRequest("p@test.com", password))

        assert(response.accessToken.isNotBlank())
        Unit
    }

    @Test
    fun suspendedUserWithCorrectPasswordIsRejected() = runBlocking {
        coEvery { userRepository.findByEmail("p@test.com") } returns user(UserStatus.SUSPENDED)
        coEvery { userRepository.findPasswordHashByEmail("p@test.com") } returns passwordHash

        assertFailsWith<UnauthorizedException> {
            useCase(LoginRequest("p@test.com", password))
        }
        Unit
    }

    @Test
    fun inactiveUserWithCorrectPasswordIsRejected() = runBlocking {
        coEvery { userRepository.findByEmail("p@test.com") } returns user(UserStatus.INACTIVE)
        coEvery { userRepository.findPasswordHashByEmail("p@test.com") } returns passwordHash

        assertFailsWith<UnauthorizedException> {
            useCase(LoginRequest("p@test.com", password))
        }
        Unit
    }

    @Test
    fun wrongPasswordIsRejectedBeforeTheStatusCheck() = runBlocking {
        // A SUSPENDED account with the WRONG password must fail on credentials, not leak account
        // status to someone who doesn't actually know the password.
        coEvery { userRepository.findByEmail("p@test.com") } returns user(UserStatus.SUSPENDED)
        coEvery { userRepository.findPasswordHashByEmail("p@test.com") } returns passwordHash

        assertFailsWith<UnauthorizedException> {
            useCase(LoginRequest("p@test.com", "wrong-password"))
        }
        Unit
    }
}
