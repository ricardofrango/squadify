package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.AuthRepository
import com.sportssoftw.squadify.model.RegisterRequest
import com.sportssoftw.squadify.model.User

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
