package com.ricardofrango.squadify.domain.service

import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.exception.TooManyPasswordAttemptsException
import com.ricardofrango.squadify.exception.WrongPasswordException
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mindrot.jbcrypt.BCrypt
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class CurrentPasswordGuardTest {

    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val userRepository = mockk<UserRepository>()
    private val guard = CurrentPasswordGuard(userRepository, transactionManager)
    private var failures = 0

    init {
        coEvery { userRepository.findById("u1") } returns User(
            id = "u1", email = "a@club.pt", firstName = "A", lastName = "B",
            status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0)
        )
        coEvery { userRepository.findPasswordHashByEmail("a@club.pt") } returns BCrypt.hashpw("right-pass", BCrypt.gensalt())
        coEvery { userRepository.incrementPasswordCheckFailures("u1") } answers { ++failures }
        coEvery { userRepository.resetPasswordCheckFailures("u1") } answers { failures = 0 }
        coEvery { userRepository.deleteRefreshTokensForUser("u1") } just runs
    }

    @Test
    fun theRightPasswordPassesAndResetsTheCount() = runBlocking<Unit> {
        failures = 2
        guard.verify("u1", "right-pass")
        assertEquals(0, failures)
    }

    @Test
    fun theFirstTwoWrongPasswordsAreAPlainErrorWithoutSigningOut() = runBlocking<Unit> {
        repeat(2) { assertFailsWith<WrongPasswordException> { guard.verify("u1", "wrong") } }
        assertEquals(2, failures)
        coVerify(exactly = 0) { userRepository.deleteRefreshTokensForUser(any()) }
    }

    @Test
    fun theThirdWrongPasswordSignsOutEverywhereAndStartsTheCountAgain() = runBlocking<Unit> {
        repeat(2) { assertFailsWith<WrongPasswordException> { guard.verify("u1", "wrong") } }
        assertFailsWith<TooManyPasswordAttemptsException> { guard.verify("u1", "wrong") }
        coVerify(exactly = 1) { userRepository.deleteRefreshTokensForUser("u1") }
        assertEquals(0, failures)
    }

    @Test
    fun aRightPasswordInBetweenStartsTheCountAgain() = runBlocking<Unit> {
        repeat(2) { assertFailsWith<WrongPasswordException> { guard.verify("u1", "wrong") } }
        guard.verify("u1", "right-pass")
        repeat(2) { assertFailsWith<WrongPasswordException> { guard.verify("u1", "wrong") } }
        coVerify(exactly = 0) { userRepository.deleteRefreshTokensForUser(any()) }
    }
}
