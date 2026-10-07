package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.RefreshToken
import com.ricardofrango.squadify.model.User
import kotlin.time.Instant

interface UserRepository {
    suspend fun findById(id: String): User?
    suspend fun findByEmail(email: String): User?
    suspend fun findByEmails(emails: List<String>): List<User>
    suspend fun findPasswordHashByEmail(email: String): String?
    suspend fun createUser(user: User, passwordHash: String): User
    // Profile fields only -- email/status/isPlatformAdmin are never self-service editable this way.
    suspend fun updateProfile(userId: String, firstName: String, lastName: String, phone: String?): Boolean
    suspend fun updatePasswordHash(userId: String, passwordHash: String): Boolean
    suspend fun getUserRoleInClub(userId: String, clubId: String): ClubRole?
    suspend fun getUserRolesInClub(userIds: List<String>, clubId: String): Map<String, ClubRole>
    suspend fun saveRefreshToken(userId: String, token: String, expiresAt: Instant)
    /** Retrieves the stored record for [token] as-is (no expiry check) — validation is the caller's job. */
    suspend fun findRefreshToken(token: String): RefreshToken?
    suspend fun deleteRefreshToken(token: String)
    /** Signs the user out everywhere (used after a password reset). */
    suspend fun deleteRefreshTokensForUser(userId: String)
    suspend fun countAllUsers(): Long
}
