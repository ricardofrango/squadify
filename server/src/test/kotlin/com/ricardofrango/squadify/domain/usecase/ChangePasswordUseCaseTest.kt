package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.domain.service.CurrentPasswordGuard
import com.ricardofrango.squadify.exception.WrongPasswordException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ChangePasswordRequest
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mindrot.jbcrypt.BCrypt
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class ChangePasswordUseCaseTest {

    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val useCase = ChangePasswordUseCase(userRepository, transactionManager, CurrentPasswordGuard(userRepository, transactionManager))

    init {
        coEvery { userRepository.incrementPasswordCheckFailures(any()) } returns 1
        coEvery { userRepository.resetPasswordCheckFailures(any()) } returns Unit
    }

    private val userId = "u1"
    private val email = "u1@t.com"
    private val currentPassword = "old-password"
    private val currentHash = BCrypt.hashpw(currentPassword, BCrypt.gensalt())

    private fun user() = User(
        id = userId, email = email, firstName = "T", lastName = "U",
        status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0)
    )

    @Test
    fun changesThePasswordWhenTheCurrentOneVerifies() = runBlocking {
        coEvery { userRepository.findById(userId) } returns user()
        coEvery { userRepository.findPasswordHashByEmail(email) } returns currentHash
        val newHash = slot<String>()
        coEvery { userRepository.updatePasswordHash(userId, capture(newHash)) } returns true

        val result = useCase(userId, ChangePasswordRequest(currentPassword, "new-password-123"))

        assertEquals(true, result)
        assertEquals(true, BCrypt.checkpw("new-password-123", newHash.captured))
    }

    @Test
    fun aWrongCurrentPasswordIsRejected() = runBlocking {
        coEvery { userRepository.findById(userId) } returns user()
        coEvery { userRepository.findPasswordHashByEmail(email) } returns currentHash

        assertFailsWith<WrongPasswordException> {
            useCase(userId, ChangePasswordRequest("totally-wrong", "new-password-123"))
        }
        coVerify(exactly = 0) { userRepository.updatePasswordHash(any(), any()) }
    }

    @Test
    fun aNewPasswordShorterThanEightCharactersIsRejected() = runBlocking {
        assertFailsWith<ValidationException> {
            useCase(userId, ChangePasswordRequest(currentPassword, "short"))
        }
        coVerify(exactly = 0) { userRepository.findById(any()) }
    }

    @Test
    fun aNonexistentUserIsNotFound() = runBlocking {
        coEvery { userRepository.findById(userId) } returns null

        assertFailsWith<NotFoundException> {
            useCase(userId, ChangePasswordRequest(currentPassword, "new-password-123"))
        }
        Unit
    }
}
