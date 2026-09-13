package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.UnauthorizedException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.LoginRequest
import com.ricardofrango.squadify.model.LoginResponse
import com.ricardofrango.squadify.security.JwtConfig
import kotlin.time.Instant
import org.mindrot.jbcrypt.BCrypt

class LoginUseCase(
    private val userRepository: UserRepository,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(request: LoginRequest): LoginResponse = transactionManager.runInTransaction {
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

        val accessToken = JwtConfig.generateAccessToken(user.id)
        val refreshToken = JwtConfig.generateRefreshToken()
        val expiresAt = Instant.fromEpochMilliseconds(JwtConfig.getRefreshTokenExpiration())
        
        userRepository.saveRefreshToken(user.id, refreshToken, expiresAt)
        
        LoginResponse(accessToken, refreshToken, user)
    }
}
