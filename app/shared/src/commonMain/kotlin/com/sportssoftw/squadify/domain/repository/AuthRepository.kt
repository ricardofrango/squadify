package com.sportssoftw.squadify.domain.repository

import com.sportssoftw.squadify.model.*

interface AuthRepository {
    suspend fun login(request: LoginRequest): LoginResponse
    suspend fun register(request: RegisterRequest): User
    suspend fun refreshToken(request: RefreshTokenRequest): TokenResponse
    suspend fun requestPasswordReset(request: PasswordResetRequest)
    suspend fun confirmPasswordReset(request: PasswordResetConfirmRequest)
}
