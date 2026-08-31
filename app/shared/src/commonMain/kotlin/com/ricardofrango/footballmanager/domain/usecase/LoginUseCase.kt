package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.AuthRepository
import com.ricardofrango.footballmanager.model.LoginRequest
import com.ricardofrango.footballmanager.model.LoginResponse

class LoginUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): LoginResponse {
        if (email.isBlank() || password.isBlank()) {
            throw Exception("Email and password cannot be empty")
        }
        
        return authRepository.login(LoginRequest(email, password))
    }
}
