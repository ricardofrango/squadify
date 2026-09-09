package com.ricardofrango.clubmanager.domain.repository

import com.ricardofrango.clubmanager.model.ClubRole
import com.ricardofrango.clubmanager.model.RefreshToken
import com.ricardofrango.clubmanager.model.User
import kotlin.time.Instant

interface UserRepository {
    suspend fun findById(id: String): User?
    suspend fun findByEmail(email: String): User?
    suspend fun findByEmails(emails: List<String>): List<User>
    suspend fun findPasswordHashByEmail(email: String): String?
    suspend fun createUser(user: User, passwordHash: String): User
    suspend fun getUserRoleInClub(userId: String, clubId: String): ClubRole?
    suspend fun getUserRolesInClub(userIds: List<String>, clubId: String): Map<String, ClubRole>
    suspend fun saveRefreshToken(userId: String, token: String, expiresAt: Instant)
    /** Retrieves the stored record for [token] as-is (no expiry check) — validation is the caller's job. */
    suspend fun findRefreshToken(token: String): RefreshToken?
    suspend fun deleteRefreshToken(token: String)
    suspend fun countAllUsers(): Long
}
