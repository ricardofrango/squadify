package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.EmailChangeCode
import com.ricardofrango.squadify.domain.repository.EmailChangeRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.EmailService
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.domain.service.CurrentPasswordGuard
import com.ricardofrango.squadify.exception.WrongPasswordException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.EmailChangeConfirmRequest
import com.ricardofrango.squadify.model.EmailChangeRequest
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class EmailChangeUseCasesTest {

    private class FakeClock(var now: Instant) : Clock {
        override fun now() = now
        fun advance(by: Duration) { now += by }
    }

    private class FakeEmailChangeRepository : EmailChangeRepository {
        val codes = mutableMapOf<String, EmailChangeCode>()
        override suspend fun findByUser(userId: String) = codes[userId]
        override suspend fun replace(code: EmailChangeCode) { codes[code.userId] = code }
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
    private val changes = FakeEmailChangeRepository()
    private val clock = FakeClock(Instant.fromEpochMilliseconds(1_000_000_000))
    private var nextCode = "123456"

    private val request = RequestEmailChangeUseCase(userRepository, changes, emailService, transactionManager, CurrentPasswordGuard(userRepository, transactionManager), clock) { nextCode }
    private val confirm = ConfirmEmailChangeUseCase(userRepository, changes, emailService, transactionManager, clock)

    private val oldEmail = "coach@club.pt"
    private val newEmail = "coach.new@club.pt"
    private val password = "current-pass"
    private val user = User(
        id = "u1", email = oldEmail, firstName = "Ana", lastName = "S",
        status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0)
    )

    init {
        coEvery { userRepository.findById("u1") } returns user
        coEvery { userRepository.findPasswordHashByEmail(oldEmail) } returns BCrypt.hashpw(password, BCrypt.gensalt())
        coEvery { userRepository.findByEmail(any()) } returns null
        coEvery { userRepository.findByEmail(oldEmail) } returns user
        coEvery { userRepository.updateEmail(any(), any()) } returns true
        coEvery { userRepository.deleteRefreshTokensForUserExcept(any(), any()) } just runs
        coEvery { emailService.sendEmailChangeCode(any(), any(), any(), any()) } just runs
        coEvery { emailService.sendEmailChanged(any(), any(), any()) } just runs
        coEvery { userRepository.incrementPasswordCheckFailures(any()) } returns 1
        coEvery { userRepository.resetPasswordCheckFailures(any()) } just runs
    }

    // --- Step 1: request ---

    @Test
    fun aCodeIsSentToTheNewAddressAndOnlyItsHashIsStored() = runBlocking<Unit> {
        request("u1", EmailChangeRequest(" $newEmail ", password))

        coVerify(exactly = 1) { emailService.sendEmailChangeCode(newEmail, "Ana", "123456", 15) }
        val stored = assertNotNull(changes.codes["u1"])
        assertEquals(newEmail, stored.newEmail)
        assertTrue(stored.codeHash != "123456" && BCrypt.checkpw("123456", stored.codeHash))
    }

    @Test
    fun aWrongPasswordIsRejected() = runBlocking<Unit> {
        assertFailsWith<WrongPasswordException> { request("u1", EmailChangeRequest(newEmail, "wrong")) }
        coVerify(exactly = 0) { emailService.sendEmailChangeCode(any(), any(), any(), any()) }
    }

    @Test
    fun anInvalidOrUnchangedAddressIsRejected() = runBlocking<Unit> {
        assertFailsWith<ValidationException> { request("u1", EmailChangeRequest("not-an-email", password)) }
        val same = assertFailsWith<ValidationException> { request("u1", EmailChangeRequest(oldEmail, password)) }
        assertEquals(RequestEmailChangeUseCase.SAME_EMAIL, same.message)
    }

    @Test
    fun anAddressWithAnAccountIsAConflict() = runBlocking<Unit> {
        coEvery { userRepository.findByEmail(newEmail) } returns user.copy(id = "u2", email = newEmail)

        assertFailsWith<ConflictException> { request("u1", EmailChangeRequest(newEmail, password)) }
        assertTrue(changes.codes.isEmpty())
    }

    @Test
    fun aRepeatWithinAMinuteForTheSameAddressSendsNothingNew() = runBlocking<Unit> {
        request("u1", EmailChangeRequest(newEmail, password))
        clock.advance(30.seconds)
        request("u1", EmailChangeRequest(newEmail, password))
        coVerify(exactly = 1) { emailService.sendEmailChangeCode(any(), any(), any(), any()) }

        // A different address replaces the pending request straight away.
        nextCode = "654321"
        request("u1", EmailChangeRequest("other@club.pt", password))
        assertEquals("other@club.pt", changes.codes.getValue("u1").newEmail)
        coVerify { emailService.sendEmailChangeCode("other@club.pt", "Ana", "654321", 15) }
    }

    // --- Step 2: confirm ---

    @Test
    fun theRightCodeSwitchesTheEmailKeepsThisSessionAndWarnsTheOldAddress() = runBlocking<Unit> {
        request("u1", EmailChangeRequest(newEmail, password))

        val updated = confirm("u1", EmailChangeConfirmRequest(" 123 456", refreshToken = "mine"))

        assertEquals(newEmail, updated.email)
        coVerify { userRepository.updateEmail("u1", newEmail) }
        coVerify { userRepository.deleteRefreshTokensForUserExcept("u1", "mine") }
        coVerify { emailService.sendEmailChanged(oldEmail, "Ana", newEmail) }
        assertNull(changes.codes["u1"])
    }

    @Test
    fun aWrongCodeCountsAnAttemptAndFiveKillTheCode() = runBlocking<Unit> {
        request("u1", EmailChangeRequest(newEmail, password))

        val error = assertFailsWith<ValidationException> { confirm("u1", EmailChangeConfirmRequest("000000")) }
        assertEquals(ConfirmEmailChangeUseCase.INVALID_CODE, error.message)
        assertEquals(1, changes.codes.getValue("u1").attempts)

        repeat(4) { assertFailsWith<ValidationException> { confirm("u1", EmailChangeConfirmRequest("000000")) } }
        assertFailsWith<ValidationException> { confirm("u1", EmailChangeConfirmRequest("123456")) }
        coVerify(exactly = 0) { userRepository.updateEmail(any(), any()) }
    }

    @Test
    fun anExpiredCodeIsRejected() = runBlocking<Unit> {
        request("u1", EmailChangeRequest(newEmail, password))
        clock.advance(15.minutes)

        assertFailsWith<ValidationException> { confirm("u1", EmailChangeConfirmRequest("123456")) }
        coVerify(exactly = 0) { userRepository.updateEmail(any(), any()) }
    }

    @Test
    fun withoutARequestThereIsNothingToConfirm() = runBlocking<Unit> {
        assertFailsWith<ValidationException> { confirm("u1", EmailChangeConfirmRequest("123456")) }
    }

    @Test
    fun anAddressTakenInTheMeantimeIsAConflict() = runBlocking<Unit> {
        request("u1", EmailChangeRequest(newEmail, password))
        coEvery { userRepository.findByEmail(newEmail) } returns user.copy(id = "u2", email = newEmail)

        assertFailsWith<ConflictException> { confirm("u1", EmailChangeConfirmRequest("123456")) }
        coVerify(exactly = 0) { userRepository.updateEmail(any(), any()) }
    }
}
