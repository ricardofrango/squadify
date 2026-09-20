package com.ricardofrango.squadify.domain.repository

interface FcmTokenRepository {
    /**
     * Registers [token] for [userId] -- upserts on the token's own uniqueness (see `Tables.kt`): a
     * token already registered to someone else is reassigned rather than duplicated, since a token
     * identifies one app install, not one user.
     */
    suspend fun upsertToken(userId: String, token: String)

    /**
     * Removes [token] if it belongs to [userId] -- a no-op, not an error, if it doesn't exist or
     * belongs to someone else, so a client's logout flow can call this idempotently.
     */
    suspend fun deleteToken(userId: String, token: String)
}
