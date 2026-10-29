package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.FcmTokenRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

class RemoveFcmTokenUseCaseTest {

    private val fcmTokenRepository = mockk<FcmTokenRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val useCase = RemoveFcmTokenUseCase(fcmTokenRepository, transactionManager)

    @Test
    fun removesTheCallersToken() = runBlocking {
        coEvery { fcmTokenRepository.deleteToken("u1", "device-token") } returns Unit

        val result = useCase("u1", "device-token")

        assertEquals(true, result)
        coVerify { fcmTokenRepository.deleteToken("u1", "device-token") }
    }

    @Test
    fun anUnknownOrAlreadyRemovedTokenIsStillSuccess() = runBlocking {
        // deleteToken is a no-op (affects zero rows) for a token that doesn't exist or belongs to a
        // different user -- the use case doesn't treat that as an error, so a client's logout flow
        // never has to branch on this call succeeding.
        coEvery { fcmTokenRepository.deleteToken("u1", "not-mine-or-gone") } returns Unit

        val result = useCase("u1", "not-mine-or-gone")

        assertEquals(true, result)
    }
}
