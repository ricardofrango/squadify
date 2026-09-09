package com.ricardofrango.clubmanager.domain.repository

import com.ricardofrango.clubmanager.model.*

interface AuthRepository {
    suspend fun login(request: LoginRequest): LoginResponse
    suspend fun register(request: RegisterRequest): User
    suspend fun refreshToken(request: RefreshTokenRequest): TokenResponse
}
