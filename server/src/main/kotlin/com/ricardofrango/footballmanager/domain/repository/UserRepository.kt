package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.User

interface UserRepository {
    suspend fun findByEmail(email: String): User?
    suspend fun findPasswordHashByEmail(email: String): String?
    suspend fun createUser(user: User, passwordHash: String): Boolean
}
