package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.UpdateProfileRequest
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class UpdateProfileUseCaseTest {

    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val useCase = UpdateProfileUseCase(userRepository, transactionManager)

    private val userId = "u1"

    private fun user(firstName: String, lastName: String, phone: String?) = User(
        id = userId, email = "u1@t.com", phone = phone, firstName = firstName, lastName = lastName,
        status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0)
    )

    @Test
    fun updatesNameAndPhoneAndReturnsTheRefreshedUser() = runBlocking {
        coEvery { userRepository.updateProfile(userId, "Ricardo", "Frango", "912345678") } returns true
        coEvery { userRepository.findById(userId) } returns user("Ricardo", "Frango", "912345678")

        val result = useCase(userId, UpdateProfileRequest(firstName = "Ricardo", lastName = "Frango", phone = "912345678"))

        assertEquals("Ricardo", result.firstName)
        assertEquals("Frango", result.lastName)
        assertEquals("912345678", result.phone)
    }

    @Test
    fun trimsFieldsAndTreatsABlankPhoneAsNull() = runBlocking {
        coEvery { userRepository.updateProfile(userId, "Ricardo", "Frango", null) } returns true
        coEvery { userRepository.findById(userId) } returns user("Ricardo", "Frango", null)

        useCase(userId, UpdateProfileRequest(firstName = "  Ricardo  ", lastName = "  Frango  ", phone = "   "))

        coVerify { userRepository.updateProfile(userId, "Ricardo", "Frango", null) }
    }

    @Test
    fun aBlankFirstNameIsRejected() = runBlocking {
        assertFailsWith<ValidationException> {
            useCase(userId, UpdateProfileRequest(firstName = " ", lastName = "Frango"))
        }
        coVerify(exactly = 0) { userRepository.updateProfile(any(), any(), any(), any()) }
    }

    @Test
    fun aBlankLastNameIsRejected() = runBlocking {
        assertFailsWith<ValidationException> {
            useCase(userId, UpdateProfileRequest(firstName = "Ricardo", lastName = ""))
        }
        Unit
    }
}
