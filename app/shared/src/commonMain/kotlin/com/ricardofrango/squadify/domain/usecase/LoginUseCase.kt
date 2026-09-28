package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AuthRepository
import com.ricardofrango.squadify.domain.repository.SessionRepository
import com.ricardofrango.squadify.model.LoginRequest
import com.ricardofrango.squadify.model.LoginResponse
import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.clearAuthTokens

class LoginUseCase(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
    private val httpClient: HttpClient
) {
    suspend operator fun invoke(email: String, password: String): LoginResponse {
        if (email.isBlank() || password.isBlank()) {
            throw Exception("Email and password cannot be empty")
        }

        val response = authRepository.login(LoginRequest(email, password))

        // Save session tokens
        sessionRepository.saveToken(response.accessToken)
        sessionRepository.saveRefreshToken(response.refreshToken)

        // The authenticated client's Auth/bearer provider caches whatever loadTokens() returned the
        // first time it ran, for the life of the client instance -- without this, logging in again on
        // the same still-running app process (e.g. right after a logout) would keep sending whichever
        // tokens were cached from *before* this login, not the ones just saved above.
        httpClient.clearAuthTokens()

        return response
    }
}
