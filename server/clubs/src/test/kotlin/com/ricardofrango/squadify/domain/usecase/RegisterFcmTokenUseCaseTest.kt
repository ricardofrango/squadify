package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.FcmTokenRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ValidationException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RegisterFcmTokenUseCaseTest {

    private val fcmTokenRepository = mockk<FcmTokenRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val useCase = RegisterFcmTokenUseCase(fcmTokenRepository, transactionManager)

    @Test
    fun registersTheCallersToken() = runBlocking {
        coEvery { fcmTokenRepository.upsertToken("u1", "device-token") } returns Unit

        val result = useCase("u1", "device-token")

        assertEquals(true, result)
        coVerify { fcmTokenRepository.upsertToken("u1", "device-token") }
    }

    @Test
    fun blankTokenIsRejected() = runBlocking {
        assertFailsWith<ValidationException> { useCase("u1", "") }
        coVerify(exactly = 0) { fcmTokenRepository.upsertToken(any(), any()) }
        Unit
    }
}
