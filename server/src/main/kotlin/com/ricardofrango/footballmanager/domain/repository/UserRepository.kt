package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.Role
import com.ricardofrango.footballmanager.model.User
import kotlin.time.Instant

interface UserRepository {
    suspend fun findById(id: String): User?
    suspend fun findByEmail(email: String): User?
    suspend fun findPasswordHashByEmail(email: String): String?
    suspend fun createUser(user: User, passwordHash: String): User
    suspend fun getUserRoleInClub(userId: String, clubId: String): Role?
    suspend fun saveRefreshToken(userId: String, token: String, expiresAt: Instant)
    suspend fun findUserIdByRefreshToken(token: String): String?
    suspend fun deleteRefreshToken(token: String)
}
