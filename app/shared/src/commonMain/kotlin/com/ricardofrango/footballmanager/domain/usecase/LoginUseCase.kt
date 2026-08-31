package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.AuthRepository
import com.ricardofrango.footballmanager.domain.repository.SessionRepository
import com.ricardofrango.footballmanager.model.LoginRequest
import com.ricardofrango.footballmanager.model.LoginResponse

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
