package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.model.LoginRequest
import com.ricardofrango.footballmanager.model.LoginResponse
import com.ricardofrango.footballmanager.security.JwtConfig
import org.mindrot.jbcrypt.BCrypt

class LoginUseCase(private val userRepository: UserRepository) {
    suspend operator fun invoke(request: LoginRequest): Result<LoginResponse> {
        if (request.email.isBlank() || request.password.isBlank()) {
            return Result.failure(IllegalArgumentException("Email and password are required"))
        }

        val user = userRepository.findByEmail(request.email)
            ?: return Result.failure(IllegalArgumentException("Invalid email or password"))

        val passwordHash = userRepository.findPasswordHashByEmail(request.email)
            ?: return Result.failure(IllegalArgumentException("Invalid email or password"))

        if (!BCrypt.checkpw(request.password, passwordHash)) {
            return Result.failure(IllegalArgumentException("Invalid email or password"))
        }

        val token = JwtConfig.generateToken(user.id)
        return Result.success(LoginResponse(token, user))
    }
}
