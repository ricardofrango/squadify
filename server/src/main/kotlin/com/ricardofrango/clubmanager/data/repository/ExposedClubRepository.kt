package com.ricardofrango.clubmanager.data.repository

import com.ricardofrango.clubmanager.data.database.ClubMembers
import com.ricardofrango.clubmanager.data.database.Clubs
import com.ricardofrango.clubmanager.data.database.Players
import com.ricardofrango.clubmanager.data.database.UserPlayers
import com.ricardofrango.clubmanager.domain.repository.ClubRepository
import com.ricardofrango.clubmanager.exception.DatabaseException
import com.ricardofrango.clubmanager.model.Club
import com.ricardofrango.clubmanager.model.ClubRole
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

class ExposedClubRepository : ClubRepository {

    override suspend fun getAllClubs(): List<Club> =
        Clubs.selectAll().map(::toClub)

    override suspend fun getClubsByUser(userId: String): List<Club> {
        val userUuid = Uuid.parse(userId)

        // One row per club, via two correlated EXISTS conditions (member OR parent of a player in
        // the club). A LEFT JOIN + withDistinct() version was tried but fans out: when the same user
        // is both a member and a linked parent, the join yields several wide rows for the one club
        // that DISTINCT can't collapse, so the club came back duplicated.
        return Clubs.selectAll().where {
            exists(
                ClubMembers.selectAll().where {
                    (ClubMembers.clubId eq Clubs.id) and (ClubMembers.userId eq userUuid)
                }
            ) or exists(
                (Players innerJoin UserPlayers).selectAll().where {
                    (Players.clubId eq Clubs.id) and (UserPlayers.userId eq userUuid)
                }
            )
        }.map(::toClub)
    }

    override suspend fun createClub(club: Club): Club =
        Clubs.insert {
            it[id] = Uuid.parse(club.id)
            it[name] = club.name
            it[shortName] = club.shortName
            it[logoUrl] = club.logoUrl
            it[email] = club.email
            it[phone] = club.phone
            it[address] = club.address
            it[status] = club.status
            it[sport] = club.sport
            it[createdAt] = club.createdAt
        }.resultedValues?.singleOrNull()?.let(::toClub)
            ?: throw DatabaseException("Failed to create club")

    override suspend fun getClubById(id: String): Club? =
        Clubs.selectAll().where { Clubs.id eq Uuid.parse(id) }
            .map(::toClub)
            .singleOrNull()

    override suspend fun updateClub(club: Club): Boolean =
        Clubs.update({ Clubs.id eq Uuid.parse(club.id) }) {
            it[name] = club.name
            it[shortName] = club.shortName
            it[logoUrl] = club.logoUrl
            it[email] = club.email
            it[phone] = club.phone
            it[address] = club.address
            it[status] = club.status
        } > 0

    override suspend fun addMember(clubId: String, userId: String, role: ClubRole): Boolean =
        ClubMembers.insert {
            it[id] = Uuid.random()
            it[this.clubId] = Uuid.parse(clubId)
            it[this.userId] = Uuid.parse(userId)
            it[this.role] = role
            it[active] = true
        }.insertedCount > 0

    override suspend fun addMembers(clubId: String, userIds: List<Pair<String, ClubRole>>): Boolean =
        ClubMembers.batchInsert(userIds) { (uId, role) ->
            this[ClubMembers.id] = Uuid.random()
            this[ClubMembers.clubId] = Uuid.parse(clubId)
            this[ClubMembers.userId] = Uuid.parse(uId)
            this[ClubMembers.role] = role
            this[ClubMembers.active] = true
        }.size == userIds.size

    override suspend fun updateMemberRole(clubId: String, userId: String, role: ClubRole): Boolean =
        ClubMembers.update({ (ClubMembers.clubId eq Uuid.parse(clubId)) and (ClubMembers.userId eq Uuid.parse(userId)) }) {
            it[this.role] = role
        } > 0

    override suspend fun countAllClubs(): Long =
        Clubs.selectAll().count()

    override suspend fun getRecentClubs(limit: Int): List<Club> =
        Clubs.selectAll()
            .orderBy(Clubs.createdAt, SortOrder.DESC)
            .limit(limit)
            .map(::toClub)

    private fun toClub(row: ResultRow): Club = Club(
        id = row[Clubs.id].toString(),
        name = row[Clubs.name],
        shortName = row[Clubs.shortName],
        logoUrl = row[Clubs.logoUrl],
        email = row[Clubs.email],
        phone = row[Clubs.phone],
        address = row[Clubs.address],
        status = row[Clubs.status],
        createdAt = row[Clubs.createdAt],
        sport = row[Clubs.sport]
    )
}
