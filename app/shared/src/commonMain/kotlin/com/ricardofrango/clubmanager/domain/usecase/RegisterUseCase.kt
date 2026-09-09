package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.AuthRepository
import com.ricardofrango.clubmanager.model.RegisterRequest
import com.ricardofrango.clubmanager.model.User

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
