package com.ricardofrango.squadify.data.repository

import com.ricardofrango.squadify.model.Country
import com.ricardofrango.squadify.data.database.ClubMembers
import com.ricardofrango.squadify.data.database.Clubs
import com.ricardofrango.squadify.data.database.Players
import com.ricardofrango.squadify.data.database.UserPlayers
import com.ricardofrango.squadify.data.database.Users
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.exception.DatabaseException
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubTheme
import com.ricardofrango.squadify.model.ClubMemberSummary
import com.ricardofrango.squadify.model.ClubRole
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid
import com.ricardofrango.squadify.util.toUuid

class ExposedClubRepository : ClubRepository {

    override suspend fun getAllClubs(): List<Club> =
        Clubs.selectAll().map(::toClub)

    override suspend fun getClubsByUser(userId: String): List<Club> {
        val userUuid = userId.toUuid()

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
            it[id] = club.id.toUuid()
            it[name] = club.name
            it[shortName] = club.shortName
            it[logoUrl] = club.logoUrl
            it[email] = club.email
            it[phone] = club.phone
            it[address] = club.address
            it[stadiumLocation] = club.stadiumLocation
            it[country] = club.country.code
            it[status] = club.status
            it[sport] = club.sport
            it[createdAt] = club.createdAt
            it[themePrimaryColor] = club.theme?.primaryColor
            it[themeSecondaryColor] = club.theme?.secondaryColor
        }.resultedValues?.singleOrNull()?.let(::toClub)
            ?: throw DatabaseException("Failed to create club")

    override suspend fun getClubById(id: String): Club? =
        Clubs.selectAll().where { Clubs.id eq id.toUuid() }
            .map(::toClub)
            .singleOrNull()

    override suspend fun updateClub(club: Club): Boolean =
        Clubs.update({ Clubs.id eq club.id.toUuid() }) {
            it[name] = club.name
            it[shortName] = club.shortName
            it[logoUrl] = club.logoUrl
            it[email] = club.email
            it[phone] = club.phone
            it[address] = club.address
            it[stadiumLocation] = club.stadiumLocation
            it[country] = club.country.code
            it[status] = club.status
            it[themePrimaryColor] = club.theme?.primaryColor
            it[themeSecondaryColor] = club.theme?.secondaryColor
        } > 0

    override suspend fun updateLogoUrl(clubId: String, logoUrl: String?): Boolean =
        Clubs.update({ Clubs.id eq clubId.toUuid() }) { it[Clubs.logoUrl] = logoUrl } > 0

    // Both add-member methods skip a (club_id, user_id) pair that already has a membership
    // (ON CONFLICT DO NOTHING on unique_club_member) rather than failing or duplicating it. The existing
    // row, and its role, is left untouched, so re-adding someone never downgrades them.
    override suspend fun addMember(clubId: String, userId: String, role: ClubRole): Boolean =
        ClubMembers.insertIgnore {
            it[id] = Uuid.random()
            it[this.clubId] = clubId.toUuid()
            it[this.userId] = userId.toUuid()
            it[this.role] = role
            it[active] = true
        }.insertedCount > 0

    override suspend fun addMembers(clubId: String, userIds: List<Pair<String, ClubRole>>): Boolean {
        ClubMembers.batchInsert(userIds, ignore = true) { (uId, role) ->
            this[ClubMembers.id] = Uuid.random()
            this[ClubMembers.clubId] = clubId.toUuid()
            this[ClubMembers.userId] = uId.toUuid()
            this[ClubMembers.role] = role
            this[ClubMembers.active] = true
        }
        // Skipped pairs aren't failures -- every requested user ends up a member either way.
        return true
    }

    override suspend fun getClubMembers(clubId: String): List<ClubMemberSummary> =
        (ClubMembers innerJoin Users).selectAll()
            .where { (ClubMembers.clubId eq clubId.toUuid()) and (ClubMembers.active eq true) }
            .orderBy(Users.firstName)
            .map {
                ClubMemberSummary(
                    userId = it[Users.id].toString(),
                    firstName = it[Users.firstName],
                    lastName = it[Users.lastName],
                    email = it[Users.email],
                    role = it[ClubMembers.role]
                )
            }

    override suspend fun updateMemberRole(clubId: String, userId: String, role: ClubRole): Boolean =
        ClubMembers.update({ (ClubMembers.clubId eq clubId.toUuid()) and (ClubMembers.userId eq userId.toUuid()) }) {
            it[this.role] = role
        } > 0

    override suspend fun removeMember(clubId: String, userId: String): Boolean =
        ClubMembers.deleteWhere { (ClubMembers.clubId eq clubId.toUuid()) and (ClubMembers.userId eq userId.toUuid()) } > 0

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
        stadiumLocation = row[Clubs.stadiumLocation],
        country = Country.fromCode(row[Clubs.country]),
        status = row[Clubs.status],
        createdAt = row[Clubs.createdAt],
        sport = row[Clubs.sport],
        theme = row[Clubs.themePrimaryColor]?.let { primary ->
            row[Clubs.themeSecondaryColor]?.let { ClubTheme(primary, it) }
        }
    )
}
