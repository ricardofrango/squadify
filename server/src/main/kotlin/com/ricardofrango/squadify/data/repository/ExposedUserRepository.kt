package com.ricardofrango.squadify.data.repository

import com.ricardofrango.squadify.data.database.ClubMembers
import com.ricardofrango.squadify.data.database.RefreshTokens
import com.ricardofrango.squadify.data.database.Users
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.exception.DatabaseException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.RefreshToken
import com.ricardofrango.squadify.model.User
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant
import kotlin.uuid.Uuid
import com.ricardofrango.squadify.util.toUuid

class ExposedUserRepository : UserRepository {

    override suspend fun findById(id: String): User? =
        Users.selectAll()
            .where { Users.id eq id.toUuid() }
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
            it[id] = user.id.toUuid()
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

    override suspend fun updateProfile(userId: String, firstName: String, lastName: String, phone: String?): Boolean =
        Users.update({ Users.id eq userId.toUuid() }) {
            it[Users.firstName] = firstName
            it[Users.lastName] = lastName
            it[Users.phone] = phone
        } > 0

    override suspend fun updatePasswordHash(userId: String, passwordHash: String): Boolean =
        Users.update({ Users.id eq userId.toUuid() }) {
            it[Users.passwordHash] = passwordHash
        } > 0

    override suspend fun getUserRoleInClub(userId: String, clubId: String): ClubRole? =
        ClubMembers.selectAll().where {
            (ClubMembers.userId eq userId.toUuid()) and (ClubMembers.clubId eq clubId.toUuid())
        }
            .map { it[ClubMembers.role] }
            .singleOrNull()

    override suspend fun getUserRolesInClub(
        userIds: List<String>,
        clubId: String
    ): Map<String, ClubRole> =
        ClubMembers.selectAll()
            .where {
                (ClubMembers.userId inList userIds.map(String::toUuid)) and (ClubMembers.clubId eq clubId.toUuid())
            }
            .associate { it[ClubMembers.userId].toString() to it[ClubMembers.role] }

    override suspend fun saveRefreshToken(userId: String, token: String, expiresAt: Instant) {
        RefreshTokens.insert {
            it[id] = Uuid.random()
            it[RefreshTokens.userId] = userId.toUuid()
            it[RefreshTokens.token] = token
            it[RefreshTokens.expiresAt] = expiresAt
        }
    }

    override suspend fun findRefreshToken(token: String): RefreshToken? =
        RefreshTokens.selectAll().where { RefreshTokens.token eq token }
            .map { RefreshToken(userId = it[RefreshTokens.userId].toString(), expiresAt = it[RefreshTokens.expiresAt]) }
            .singleOrNull()

    override suspend fun deleteRefreshToken(token: String) {
        RefreshTokens.deleteWhere { RefreshTokens.token eq token }
    }

    override suspend fun deleteRefreshTokensForUser(userId: String) {
        RefreshTokens.deleteWhere { RefreshTokens.userId eq userId.toUuid() }
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
