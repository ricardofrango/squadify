package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.data.remote.ApiException
import com.ricardofrango.squadify.domain.repository.AuthRepository
import com.ricardofrango.squadify.domain.repository.SessionRepository
import com.ricardofrango.squadify.model.RefreshTokenRequest

/**
 * Run once at app start (Splash). Returns whether the user should stay signed in.
 *
 * Only a 401 from the refresh endpoint means the session is really over (refresh token unknown,
 * expired, or revoked), so only that clears it. Any other failure -- no network, server unreachable
 * (e.g. on mobile data against a LAN dev server), a 5xx -- keeps the stored session and lets the user
 * in; the next screen shows its own retryable error, and the authenticated client refreshes on the
 * next 401 once the server is reachable again.
 */
class CheckAndRefreshSessionUseCase(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(): Boolean {
        val refreshToken = sessionRepository.getRefreshToken() ?: return false

        return try {
            val response = authRepository.refreshToken(RefreshTokenRequest(refreshToken))
            sessionRepository.saveToken(response.accessToken)
            sessionRepository.saveRefreshToken(response.refreshToken)
            true
        } catch (e: ApiException) {
            if (e.statusCode == 401) {
                sessionRepository.clearSession()
                false
            } else {
                true
            }
        } catch (e: Exception) {
            true
        }
    }
}
