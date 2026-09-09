package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.UserRepository
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.exception.UnauthorizedException
import com.ricardofrango.clubmanager.exception.ValidationException
import com.ricardofrango.clubmanager.model.LoginRequest
import com.ricardofrango.clubmanager.model.LoginResponse
import com.ricardofrango.clubmanager.security.JwtConfig
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
