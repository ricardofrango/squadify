package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.*

interface AuthRepository {
    suspend fun login(request: LoginRequest): LoginResponse
    suspend fun register(request: RegisterRequest): User
    suspend fun refreshToken(request: RefreshTokenRequest): TokenResponse
}
