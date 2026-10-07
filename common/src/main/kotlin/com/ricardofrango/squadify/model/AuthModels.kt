package com.ricardofrango.squadify.model

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

/** Body of `PATCH /users/me` -- the caller is always "self", resolved from the JWT, never a path id. */
@Serializable
data class UpdateProfileRequest(
    val firstName: String,
    val lastName: String,
    val phone: String? = null
)

/**
 * Body of `POST /users/me/password` -- [currentPassword] must verify against the caller's own stored
 * hash (same `BCrypt.checkpw` gate `LoginUseCase` uses) before [newPassword] is accepted, so a stolen
 * access token alone isn't enough to lock the real owner out.
 */
@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

/**
 * Body of `POST /auth/password-reset/request` (Forgot password). 202 when the code was sent, 404
 * when [email] has no active account.
 */
@Serializable
data class PasswordResetRequest(val email: String)

/**
 * Body of `POST /auth/password-reset/confirm`: the 6-digit [code] emailed by the request above, and
 * the [newPassword] (8+ characters, same rule as Change password).
 */
@Serializable
data class PasswordResetConfirmRequest(
    val email: String,
    val code: String,
    val newPassword: String
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
