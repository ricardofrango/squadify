package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.UserRepository
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.exception.UnauthorizedException
import com.ricardofrango.clubmanager.model.TokenResponse
import com.ricardofrango.clubmanager.security.JwtConfig
import kotlin.time.Instant

class RefreshTokenUseCase(
    private val userRepository: UserRepository,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(refreshToken: String): TokenResponse = transactionManager.runInTransaction {
        val stored = userRepository.findRefreshToken(refreshToken)
            ?: throw UnauthorizedException("Invalid refresh token")

        // Business rule: a refresh token is only usable until its stored expiry. An expired token is
        // dropped and rejected the same way an unknown one is.
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        if (stored.expiresAt < now) {
            userRepository.deleteRefreshToken(refreshToken)
            throw UnauthorizedException("Refresh token expired")
        }

        // Rotate: drop the presented token and issue a fresh pair.
        userRepository.deleteRefreshToken(refreshToken)
        val userId = stored.userId

        val newAccessToken = JwtConfig.generateAccessToken(userId)
        val newRefreshToken = JwtConfig.generateRefreshToken()
        val expiresAt = Instant.fromEpochMilliseconds(JwtConfig.getRefreshTokenExpiration())
        
        userRepository.saveRefreshToken(userId, newRefreshToken, expiresAt)
        
        TokenResponse(newAccessToken, newRefreshToken)
    }
}
