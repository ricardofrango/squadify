package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.AuthRepository
import com.ricardofrango.clubmanager.domain.repository.SessionRepository
import com.ricardofrango.clubmanager.model.RefreshTokenRequest

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
        } catch (e: Exception) {
            sessionRepository.clearSession()
            false
        }
    }
}
