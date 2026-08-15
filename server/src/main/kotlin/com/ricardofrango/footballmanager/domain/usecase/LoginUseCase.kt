package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.UnauthorizedException
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.LoginRequest
import com.ricardofrango.footballmanager.model.LoginResponse
import com.ricardofrango.footballmanager.security.JwtConfig
import org.mindrot.jbcrypt.BCrypt

class LoginUseCase(private val userRepository: UserRepository) {
    suspend operator fun invoke(request: LoginRequest): LoginResponse {
        if (request.email.isBlank() || request.password.isBlank()) {
            throw ValidationException("Email and password are required")
        }

        val user = userRepository.findByEmail(request.email)
            ?: throw UnauthorizedException("Invalid email or password")

        val passwordHash = userRepository.findPasswordHashByEmail(request.email)
            ?: throw UnauthorizedException("Invalid email or password")

        if (!BCrypt.checkpw(request.password, passwordHash)) {
            throw UnauthorizedException("Invalid email or password")
        }

        val token = JwtConfig.generateToken(user.id)
        return LoginResponse(token, user)
    }
}
