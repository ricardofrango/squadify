package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AuthRepository
import com.ricardofrango.squadify.domain.repository.SessionRepository
import com.ricardofrango.squadify.model.LoginRequest
import com.ricardofrango.squadify.model.LoginResponse

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
