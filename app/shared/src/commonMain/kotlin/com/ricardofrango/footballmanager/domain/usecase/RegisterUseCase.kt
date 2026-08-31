package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.AuthRepository
import com.ricardofrango.footballmanager.model.RegisterRequest
import com.ricardofrango.footballmanager.model.User

class RegisterUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(request: RegisterRequest): User {
        if (request.email.isBlank() || request.password.isBlank() || 
            request.firstName.isBlank() || request.lastName.isBlank()) {
            throw Exception("Required fields cannot be empty")
        }
        
        return authRepository.register(request)
    }
}
