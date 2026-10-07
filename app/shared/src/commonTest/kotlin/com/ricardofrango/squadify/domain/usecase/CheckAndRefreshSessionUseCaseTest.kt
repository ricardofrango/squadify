package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.model.PasswordResetConfirmRequest
import com.ricardofrango.squadify.model.PasswordResetRequest
import com.ricardofrango.squadify.data.remote.ApiException
import com.ricardofrango.squadify.domain.repository.AuthRepository
import com.ricardofrango.squadify.domain.repository.SessionRepository
import com.ricardofrango.squadify.model.LoginRequest
import com.ricardofrango.squadify.model.LoginResponse
import com.ricardofrango.squadify.model.RefreshTokenRequest
import com.ricardofrango.squadify.model.RegisterRequest
import com.ricardofrango.squadify.model.TokenResponse
import com.ricardofrango.squadify.model.User
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The app-start session check: only a 401 from the refresh endpoint ends the session. Being offline or
 * unable to reach the server must not log the user out.
 */
class CheckAndRefreshSessionUseCaseTest {

    private class FakeSessionRepository(
        var access: String? = "old-access",
        var refresh: String? = "old-refresh"
    ) : SessionRepository {
        override fun saveToken(token: String) { access = token }
        override fun getToken(): String? = access
        override fun saveRefreshToken(token: String) { refresh = token }
        override fun getRefreshToken(): String? = refresh
        override fun clearSession() { access = null; refresh = null }
    }

    private class FakeAuthRepository(private val onRefresh: () -> TokenResponse) : AuthRepository {
        override suspend fun login(request: LoginRequest): LoginResponse = error("unused")
        override suspend fun register(request: RegisterRequest): User = error("unused")
        override suspend fun refreshToken(request: RefreshTokenRequest): TokenResponse = onRefresh()
        override suspend fun requestPasswordReset(request: PasswordResetRequest) = Unit
        override suspend fun confirmPasswordReset(request: PasswordResetConfirmRequest) = Unit
    }

    @Test
    fun aSuccessfulRefreshStoresTheNewTokensAndStaysSignedIn() = runTest {
        val session = FakeSessionRepository()
        val useCase = CheckAndRefreshSessionUseCase(FakeAuthRepository { TokenResponse("new-access", "new-refresh") }, session)

        assertTrue(useCase())
        assertEquals("new-access", session.access)
        assertEquals("new-refresh", session.refresh)
    }

    @Test
    fun a401FromTheRefreshEndpointClearsTheSession() = runTest {
        val session = FakeSessionRepository()
        val useCase = CheckAndRefreshSessionUseCase(
            FakeAuthRepository { throw ApiException(401, "AUTH_001", "Invalid refresh token") }, session
        )

        assertFalse(useCase())
        assertNull(session.access)
        assertNull(session.refresh)
    }

    @Test
    fun aNetworkFailureKeepsTheSessionAndStaysSignedIn() = runTest {
        val session = FakeSessionRepository()
        val useCase = CheckAndRefreshSessionUseCase(
            FakeAuthRepository { throw RuntimeException("Unable to resolve host") }, session
        )

        assertTrue(useCase())
        assertEquals("old-access", session.access)
        assertEquals("old-refresh", session.refresh)
    }

    @Test
    fun aServerErrorKeepsTheSessionAndStaysSignedIn() = runTest {
        val session = FakeSessionRepository()
        val useCase = CheckAndRefreshSessionUseCase(
            FakeAuthRepository { throw ApiException(503, null, "Service unavailable") }, session
        )

        assertTrue(useCase())
        assertEquals("old-refresh", session.refresh)
    }

    @Test
    fun noStoredRefreshTokenMeansSignedOutWithoutCallingTheServer() = runTest {
        val session = FakeSessionRepository(access = null, refresh = null)
        val useCase = CheckAndRefreshSessionUseCase(FakeAuthRepository { error("must not be called") }, session)

        assertFalse(useCase())
    }
}
