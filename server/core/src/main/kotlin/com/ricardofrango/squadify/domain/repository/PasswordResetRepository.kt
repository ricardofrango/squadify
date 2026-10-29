package com.ricardofrango.squadify.domain.repository

import kotlin.time.Instant

/** A pending Forgot-password code (server-internal; only its hash is stored). */
data class PasswordResetCode(
    val userId: String,
    val codeHash: String,
    val expiresAt: Instant,
    val attempts: Int,
    val createdAt: Instant
)

interface PasswordResetRepository {
    suspend fun findByUser(userId: String): PasswordResetCode?
    /** Stores [code] as the user's only pending code, replacing any earlier one. */
    suspend fun replace(code: PasswordResetCode)
    suspend fun incrementAttempts(userId: String)
    suspend fun delete(userId: String)
}
