package com.ricardofrango.squadify.domain.repository

import kotlin.time.Instant

/** A pending Change-email request (server-internal; only the code's hash is stored). */
data class EmailChangeCode(
    val userId: String,
    val newEmail: String,
    val codeHash: String,
    val expiresAt: Instant,
    val attempts: Int,
    val createdAt: Instant
)

interface EmailChangeRepository {
    suspend fun findByUser(userId: String): EmailChangeCode?
    /** Stores [code] as the user's only pending request, replacing any earlier one. */
    suspend fun replace(code: EmailChangeCode)
    suspend fun incrementAttempts(userId: String)
    suspend fun delete(userId: String)
}
