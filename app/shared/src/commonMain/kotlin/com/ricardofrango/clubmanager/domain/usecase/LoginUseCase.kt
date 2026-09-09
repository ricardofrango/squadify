package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.AuthRepository
import com.ricardofrango.clubmanager.domain.repository.SessionRepository
import com.ricardofrango.clubmanager.model.LoginRequest
import com.ricardofrango.clubmanager.model.LoginResponse

class LoginUseCase(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(email: String, password: String): LoginResponse {
        if (email.isBlank() || password.isBlank()) {
            throw Exception("Email and password cannot be empty")
        }
        
        val response = authRepository.login(LoginRequest(email, password))
        
        // Save session tokens
        sessionRepository.saveToken(response.accessToken)
        sessionRepository.saveRefreshToken(response.refreshToken)
        
        return response
    }
}
