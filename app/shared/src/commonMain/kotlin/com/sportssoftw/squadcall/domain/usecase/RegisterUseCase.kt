package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.AuthRepository
import com.sportssoftw.squadcall.model.RegisterRequest
import com.sportssoftw.squadcall.model.User

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
