package com.ricardofrango.footballmanager.data.repository

import com.ricardofrango.footballmanager.data.database.ClubMembers
import com.ricardofrango.footballmanager.data.database.RefreshTokens
import com.ricardofrango.footballmanager.data.database.Users
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.DatabaseException
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.User
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ExposedUserRepository : UserRepository {

    override suspend fun findById(id: String): User? =
        Users.selectAll()
            .where { Users.id eq Uuid.parse(id) }
            .map(::toUser)
            .singleOrNull()

    override suspend fun findByEmail(email: String): User? =
        Users.selectAll()
            .where { Users.email eq email }
            .map(::toUser)
            .singleOrNull()

    override suspend fun findByEmails(emails: List<String>): List<User> =
        Users.selectAll()
            .where { Users.email inList emails }
            .map(::toUser)

    override suspend fun findPasswordHashByEmail(email: String): String? =
        Users.selectAll()
            .where { Users.email eq email }
            .map { it[Users.passwordHash] }
            .singleOrNull()

    override suspend fun createUser(user: User, passwordHash: String): User =
        Users.insert {
            it[id] = Uuid.parse(user.id)
            it[email] = user.email
            it[Users.passwordHash] = passwordHash
            it[phone] = user.phone
            it[firstName] = user.firstName
            it[lastName] = user.lastName
            it[avatarUrl] = user.avatarUrl
            it[status] = user.status
            it[isPlatformAdmin] = user.isPlatformAdmin
            it[createdAt] = user.createdAt
        }.resultedValues
            ?.singleOrNull()
            ?.let(::toUser)
            ?: throw DatabaseException("Failed to create user")

    override suspend fun getUserRoleInClub(userId: String, clubId: String): ClubRole? =
        ClubMembers.selectAll().where {
            (ClubMembers.userId eq Uuid.parse(userId)) and (ClubMembers.clubId eq Uuid.parse(clubId))
        }
            .map { it[ClubMembers.role] }
            .singleOrNull()

    override suspend fun getUserRolesInClub(
        userIds: List<String>,
        clubId: String
    ): Map<String, ClubRole> =
        ClubMembers.selectAll()
            .where {
                (ClubMembers.userId inList userIds.map(Uuid::parse)) and (ClubMembers.clubId eq Uuid.parse(clubId))
            }
            .associate { it[ClubMembers.userId].toString() to it[ClubMembers.role] }

    override suspend fun saveRefreshToken(userId: String, token: String, expiresAt: Instant) {
        RefreshTokens.insert {
            it[id] = Uuid.random()
            it[RefreshTokens.userId] = Uuid.parse(userId)
            it[RefreshTokens.token] = token
            it[RefreshTokens.expiresAt] = expiresAt
        }
    }

    override suspend fun findUserIdByRefreshToken(token: String): String? =
        RefreshTokens.selectAll().where { RefreshTokens.token eq token }
            .map { it[RefreshTokens.userId].toString() }
            .singleOrNull()

    override suspend fun deleteRefreshToken(token: String) {
        RefreshTokens.deleteWhere { RefreshTokens.token eq token }
    }

    override suspend fun countAllUsers(): Long =
        Users.selectAll().count()

    private fun toUser(row: ResultRow): User = User(
        id = row[Users.id].toString(),
        email = row[Users.email],
        phone = row[Users.phone],
        firstName = row[Users.firstName],
        lastName = row[Users.lastName],
        avatarUrl = row[Users.avatarUrl],
        status = row[Users.status],
        isPlatformAdmin = row[Users.isPlatformAdmin],
        createdAt = row[Users.createdAt]
    )
}
