package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.User
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
    suspend fun findUserIdByRefreshToken(token: String): String?
    suspend fun deleteRefreshToken(token: String)
    suspend fun countAllUsers(): Long
}
