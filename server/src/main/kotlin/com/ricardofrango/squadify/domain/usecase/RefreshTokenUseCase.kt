package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.UnauthorizedException
import com.ricardofrango.squadify.model.TokenResponse
import com.ricardofrango.squadify.security.JwtConfig
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
