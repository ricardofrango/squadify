package com.ricardofrango.clubmanager.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: User
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val phone: String? = null
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String
)

/**
 * A stored refresh-token record, as retrieved for validation. Server-internal (never returned to a
 * client) — carries just what `RefreshTokenUseCase` needs to decide whether the token is still usable.
 */
@Serializable
data class RefreshToken(
    val userId: String,
    val expiresAt: Instant
)

@Serializable
data class ProvisionClubRequest(
    val directorEmail: String,
    val clubName: String,
    val sport: SportType
)
