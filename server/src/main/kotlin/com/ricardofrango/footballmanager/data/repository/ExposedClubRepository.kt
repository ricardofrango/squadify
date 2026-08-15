package com.ricardofrango.footballmanager.data.repository

import com.ricardofrango.footballmanager.data.database.Clubs
import com.ricardofrango.footballmanager.data.database.DatabaseFactory.dbQuery
import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.model.Club
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.Uuid

class ExposedClubRepository : ClubRepository {

    override suspend fun getAllClubs(): List<Club> = dbQuery {
        Clubs.selectAll().map(::toClub)
    }

    override suspend fun createClub(club: Club): Boolean = dbQuery {
        val insertStatement = Clubs.insert {
            it[Clubs.id] = Uuid.parse(club.id)
            it[Clubs.name] = club.name
            it[Clubs.shortName] = club.shortName
            it[Clubs.logoUrl] = club.logoUrl
            it[Clubs.email] = club.email
            it[Clubs.phone] = club.phone
            it[Clubs.address] = club.address
            it[Clubs.status] = club.status
            it[Clubs.createdAt] = club.createdAt
        }
        insertStatement.insertedCount > 0
    }

    override suspend fun getClubById(id: String): Club? = dbQuery {
        Clubs.selectAll().where { Clubs.id eq Uuid.parse(id) }
            .map(::toClub)
            .singleOrNull()
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
