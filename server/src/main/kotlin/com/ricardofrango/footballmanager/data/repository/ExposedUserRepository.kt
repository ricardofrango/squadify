package com.ricardofrango.footballmanager.data.repository

import com.ricardofrango.footballmanager.data.database.ClubMembers
import com.ricardofrango.footballmanager.data.database.DatabaseFactory.dbQuery
import com.ricardofrango.footballmanager.data.database.Users
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.model.Role
import com.ricardofrango.footballmanager.model.User
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.Uuid

class ExposedUserRepository : UserRepository {

    override suspend fun findByEmail(email: String): User? = dbQuery {
        Users.selectAll().where { Users.email eq email }
            .map(::toUser)
            .singleOrNull()
    }

    override suspend fun findPasswordHashByEmail(email: String): String? = dbQuery {
        Users.selectAll().where { Users.email eq email }
            .map { it[Users.passwordHash] }
            .singleOrNull()
    }

    override suspend fun createUser(user: User, passwordHash: String): Boolean = dbQuery {
        val insertStatement = Users.insert {
            it[id] = Uuid.parse(user.id)
            it[email] = user.email
            it[Users.passwordHash] = passwordHash
            it[phone] = user.phone
            it[firstName] = user.firstName
            it[lastName] = user.lastName
            it[avatarUrl] = user.avatarUrl
            it[status] = user.status
            it[createdAt] = user.createdAt
        }
        insertStatement.insertedCount > 0
    }

    override suspend fun getUserRoleInClub(userId: String, clubId: String): Role? = dbQuery {
        ClubMembers.selectAll().where { 
            (ClubMembers.userId eq Uuid.parse(userId)) and (ClubMembers.clubId eq Uuid.parse(clubId)) 
        }.map { it[ClubMembers.role] }
        .singleOrNull()
    }

    private fun toUser(row: ResultRow): User = User(
        id = row[Users.id].toString(),
        email = row[Users.email],
        phone = row[Users.phone],
        firstName = row[Users.firstName],
        lastName = row[Users.lastName],
        avatarUrl = row[Users.avatarUrl],
        status = row[Users.status],
        createdAt = row[Users.createdAt]
    )
}
