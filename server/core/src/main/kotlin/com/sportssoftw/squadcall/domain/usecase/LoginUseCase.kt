package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.UserRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.UnauthorizedException
import com.sportssoftw.squadcall.exception.ValidationException
import com.sportssoftw.squadcall.model.LoginRequest
import com.sportssoftw.squadcall.model.LoginResponse
import com.sportssoftw.squadcall.model.UserStatus
import com.sportssoftw.squadcall.security.JwtTokens
import kotlin.time.Instant
import org.mindrot.jbcrypt.BCrypt

class LoginUseCase(
    private val userRepository: UserRepository,
    private val transactionManager: TransactionManager,
    private val tokens: JwtTokens
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

        // Checked only after the password verifies, so a caller who doesn't already know the
        // credentials can't use this to probe whether an account exists/is suspended -- same
        // status gate CheckUserPermissionUseCase applies to every already-authenticated action.
        if (user.status != UserStatus.ACTIVE) {
            throw UnauthorizedException("Your account is not active")
        }

        val accessToken = tokens.generateAccessToken(user.id)
        val refreshToken = tokens.generateRefreshToken()
        val expiresAt = Instant.fromEpochMilliseconds(tokens.getRefreshTokenExpiration())
        
        userRepository.saveRefreshToken(user.id, refreshToken, expiresAt)
        
        LoginResponse(accessToken, refreshToken, user)
    }
}
