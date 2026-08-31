package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.*

interface AuthRepository {
    suspend fun login(request: LoginRequest): LoginResponse
    suspend fun register(request: RegisterRequest): User
    suspend fun refreshToken(request: RefreshTokenRequest): TokenResponse
}
