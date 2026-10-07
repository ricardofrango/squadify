package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.PasswordResetCode
import com.ricardofrango.squadify.domain.repository.PasswordResetRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.EmailService
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.PasswordResetConfirmRequest
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mindrot.jbcrypt.BCrypt
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class PasswordResetUseCasesTest {

    private class FakeClock(var now: Instant) : Clock {
        override fun now() = now
        fun advance(by: Duration) { now += by }
    }

    private class FakeResetRepository : PasswordResetRepository {
        val codes = mutableMapOf<String, PasswordResetCode>()
        override suspend fun findByUser(userId: String) = codes[userId]
        override suspend fun replace(code: PasswordResetCode) { codes[code.userId] = code }
        override suspend fun incrementAttempts(userId: String) {
            codes[userId]?.let { codes[userId] = it.copy(attempts = it.attempts + 1) }
        }
        override suspend fun delete(userId: String) { codes.remove(userId) }
    }

    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val userRepository = mockk<UserRepository>()
    private val emailService = mockk<EmailService>()
    private val resets = FakeResetRepository()
    private val clock = FakeClock(Instant.fromEpochMilliseconds(1_000_000_000))
    private var nextCode = "123456"

    private val request = RequestPasswordResetUseCase(userRepository, resets, emailService, transactionManager, clock) { nextCode }
    private val reset = ResetPasswordUseCase(userRepository, resets, transactionManager, clock)

    private val email = "coach@club.pt"
    private fun user(status: UserStatus = UserStatus.ACTIVE) = User(
        id = "u1", email = email, firstName = "Ana", lastName = "S",
        status = status, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0)
    )

    init {
        coEvery { emailService.sendPasswordResetCode(any(), any(), any(), any()) } just runs
        coEvery { userRepository.findByEmail(any()) } returns null
        coEvery { userRepository.findByEmail(email) } returns user()
        coEvery { userRepository.updatePasswordHash(any(), any()) } returns true
        coEvery { userRepository.deleteRefreshTokensForUser(any()) } just runs
    }

    // --- Step 1: request a code ---

    @Test
    fun aKnownEmailGetsAHashedCodeByEmail() = runBlocking<Unit> {
        request("  $email ")

        coVerify(exactly = 1) { emailService.sendPasswordResetCode(email, "Ana", "123456", 15) }
        val stored = assertNotNull(resets.codes["u1"])
        assertTrue(stored.codeHash != "123456" && BCrypt.checkpw("123456", stored.codeHash))
        assertEquals(clock.now + 15.minutes, stored.expiresAt)
    }

    @Test
    fun anUnknownEmailIsNotFoundAndSendsNothing() = runBlocking<Unit> {
        val error = assertFailsWith<NotFoundException> { request("nobody@club.pt") }

        assertEquals(RequestPasswordResetUseCase.NO_ACCOUNT, error.message)
        coVerify(exactly = 0) { emailService.sendPasswordResetCode(any(), any(), any(), any()) }
        assertTrue(resets.codes.isEmpty())
    }

    @Test
    fun aSuspendedAccountIsNotFoundAndSendsNothing() = runBlocking<Unit> {
        coEvery { userRepository.findByEmail(email) } returns user(UserStatus.SUSPENDED)

        assertFailsWith<NotFoundException> { request(email) }

        coVerify(exactly = 0) { emailService.sendPasswordResetCode(any(), any(), any(), any()) }
    }

    @Test
    fun aSecondRequestWithinAMinuteIsIgnoredAndAfterItReplacesTheCode() = runBlocking<Unit> {
        request(email)
        nextCode = "654321"
        clock.advance(30.seconds)
        request(email)
        coVerify(exactly = 1) { emailService.sendPasswordResetCode(any(), any(), any(), any()) }

        clock.advance(31.seconds)
        request(email)
        coVerify(exactly = 1) { emailService.sendPasswordResetCode(email, "Ana", "654321", 15) }
        assertTrue(BCrypt.checkpw("654321", resets.codes.getValue("u1").codeHash))
    }

    // --- Step 2: reset with the code ---

    @Test
    fun theRightCodeSetsTheNewPasswordUsesTheCodeAndSignsOutEverywhere() = runBlocking<Unit> {
        request(email)
        val hash = slot<String>()
        coEvery { userRepository.updatePasswordHash("u1", capture(hash)) } returns true

        reset(PasswordResetConfirmRequest(email, " 123 456 ", "brand-new-pass"))

        assertTrue(BCrypt.checkpw("brand-new-pass", hash.captured))
        assertNull(resets.codes["u1"])
        coVerify { userRepository.deleteRefreshTokensForUser("u1") }
    }

    @Test
    fun aWrongCodeIsRejectedAndCountsAnAttempt() = runBlocking<Unit> {
        request(email)

        val error = assertFailsWith<ValidationException> { reset(PasswordResetConfirmRequest(email, "000000", "brand-new-pass")) }

        assertEquals(ResetPasswordUseCase.INVALID_CODE, error.message)
        assertEquals(1, resets.codes.getValue("u1").attempts)
        coVerify(exactly = 0) { userRepository.updatePasswordHash(any(), any()) }
    }

    @Test
    fun afterFiveWrongCodesEvenTheRightOneIsRejected() = runBlocking<Unit> {
        request(email)
        repeat(5) { assertFailsWith<ValidationException> { reset(PasswordResetConfirmRequest(email, "000000", "brand-new-pass")) } }

        assertFailsWith<ValidationException> { reset(PasswordResetConfirmRequest(email, "123456", "brand-new-pass")) }
        coVerify(exactly = 0) { userRepository.updatePasswordHash(any(), any()) }
    }

    @Test
    fun anExpiredCodeIsRejected() = runBlocking<Unit> {
        request(email)
        clock.advance(15.minutes)

        assertFailsWith<ValidationException> { reset(PasswordResetConfirmRequest(email, "123456", "brand-new-pass")) }
        coVerify(exactly = 0) { userRepository.updatePasswordHash(any(), any()) }
    }

    @Test
    fun aCodeCanOnlyBeUsedOnce() = runBlocking<Unit> {
        request(email)
        reset(PasswordResetConfirmRequest(email, "123456", "brand-new-pass"))

        assertFailsWith<ValidationException> { reset(PasswordResetConfirmRequest(email, "123456", "another-pass-1")) }
    }

    @Test
    fun anUnknownEmailGetsTheSameErrorAsAWrongCode() = runBlocking<Unit> {
        val error = assertFailsWith<ValidationException> { reset(PasswordResetConfirmRequest("nobody@club.pt", "123456", "brand-new-pass")) }
        assertEquals(ResetPasswordUseCase.INVALID_CODE, error.message)
    }

    @Test
    fun aShortNewPasswordIsRejectedBeforeTheCodeIsChecked() = runBlocking<Unit> {
        request(email)

        assertFailsWith<ValidationException> { reset(PasswordResetConfirmRequest(email, "123456", "short")) }
        assertEquals(0, resets.codes.getValue("u1").attempts)
    }

    @Test
    fun generatedCodesAreSixDigits() {
        repeat(200) { assertTrue(Regex("\\d{6}").matches(randomResetCode())) }
    }
}
