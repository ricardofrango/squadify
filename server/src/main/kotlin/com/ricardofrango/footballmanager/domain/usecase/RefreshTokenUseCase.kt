package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.exception.UnauthorizedException
import com.ricardofrango.footballmanager.model.TokenResponse
import com.ricardofrango.footballmanager.security.JwtConfig
import kotlin.time.Instant

class RefreshTokenUseCase(
    private val userRepository: UserRepository,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(refreshToken: String): TokenResponse = transactionManager.runInTransaction {
        val userId = userRepository.findUserIdByRefreshToken(refreshToken)
            ?: throw UnauthorizedException("Invalid refresh token")

        // In a real app, we should also check if the token has expired in the DB.
        // For MVP, we can just delete the old one and generate a new pair.
        
        userRepository.deleteRefreshToken(refreshToken)
        
        val newAccessToken = JwtConfig.generateAccessToken(userId)
        val newRefreshToken = JwtConfig.generateRefreshToken()
        val expiresAt = Instant.fromEpochMilliseconds(JwtConfig.getRefreshTokenExpiration())
        
        userRepository.saveRefreshToken(userId, newRefreshToken, expiresAt)
        
        TokenResponse(newAccessToken, newRefreshToken)
    }
}
