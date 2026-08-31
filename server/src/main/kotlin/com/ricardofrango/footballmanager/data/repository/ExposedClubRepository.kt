package com.ricardofrango.footballmanager.data.repository

import com.ricardofrango.footballmanager.data.database.ClubMembers
import com.ricardofrango.footballmanager.data.database.Clubs
import com.ricardofrango.footballmanager.data.database.DatabaseFactory.dbQuery
import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.exception.DatabaseException
import com.ricardofrango.footballmanager.model.Club
import com.ricardofrango.footballmanager.model.ClubRole
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

class ExposedClubRepository : ClubRepository {

    override suspend fun getAllClubs(): List<Club> = dbQuery {
        Clubs.selectAll().map(::toClub)
    }

    override suspend fun getClubsByUser(userId: String): List<Club> = dbQuery {
        (Clubs innerJoin ClubMembers).selectAll()
            .where { ClubMembers.userId eq Uuid.parse(userId) }
            .map(::toClub)
    }

    override suspend fun createClub(club: Club): Club = dbQuery {
        Clubs.insert {
            it[id] = Uuid.parse(club.id)
            it[name] = club.name
            it[shortName] = club.shortName
            it[logoUrl] = club.logoUrl
            it[email] = club.email
            it[phone] = club.phone
            it[address] = club.address
            it[status] = club.status
            it[createdAt] = club.createdAt
        }.resultedValues?.singleOrNull()?.let(::toClub)
            ?: throw DatabaseException("Failed to create club")
    }

    override suspend fun getClubById(id: String): Club? = dbQuery {
        Clubs.selectAll().where { Clubs.id eq Uuid.parse(id) }
            .map(::toClub)
            .singleOrNull()
    }

    override suspend fun updateClub(club: Club): Boolean = dbQuery {
        Clubs.update({ Clubs.id eq Uuid.parse(club.id) }) {
            it[name] = club.name
            it[shortName] = club.shortName
            it[logoUrl] = club.logoUrl
            it[email] = club.email
            it[phone] = club.phone
            it[address] = club.address
            it[status] = club.status
        } > 0
    }

    override suspend fun addMember(clubId: String, userId: String, role: ClubRole): Boolean = dbQuery {
        val insertStatement = ClubMembers.insert {
            it[id] = Uuid.random()
            it[this.clubId] = Uuid.parse(clubId)
            it[this.userId] = Uuid.parse(userId)
            it[this.role] = role
            it[active] = true
        }
        insertStatement.insertedCount > 0
    }

    override suspend fun addMembers(clubId: String, userIds: List<Pair<String, ClubRole>>): Boolean = dbQuery {
        ClubMembers.batchInsert(userIds) { (uId, role) ->
            this[ClubMembers.id] = Uuid.random()
            this[ClubMembers.clubId] = Uuid.parse(clubId)
            this[ClubMembers.userId] = Uuid.parse(uId)
            this[ClubMembers.role] = role
            this[ClubMembers.active] = true
        }.size == userIds.size
    }

    override suspend fun updateMemberRole(clubId: String, userId: String, role: ClubRole): Boolean = dbQuery {
        ClubMembers.update({ (ClubMembers.clubId eq Uuid.parse(clubId)) and (ClubMembers.userId eq Uuid.parse(userId)) }) {
            it[this.role] = role
        } > 0
    }

    override suspend fun countAllClubs(): Long = dbQuery {
        Clubs.selectAll().count()
    }

    override suspend fun getRecentClubs(limit: Int): List<Club> = dbQuery {
        Clubs.selectAll()
            .orderBy(Clubs.createdAt, SortOrder.DESC)
            .limit(limit)
            .map(::toClub)
    }

    private fun toClub(row: ResultRow): Club = Club(
        id = row[Clubs.id].toString(),
        name = row[Clubs.name],
        shortName = row[Clubs.shortName],
        logoUrl = row[Clubs.logoUrl],
        email = row[Clubs.email],
        phone = row[Clubs.phone],
        address = row[Clubs.address],
        status = row[Clubs.status],
        createdAt = row[Clubs.createdAt]
    )
}
