package com.ricardofrango.squadify.coach.data

import com.ricardofrango.squadify.data.database.AgeGroupPlayers
import com.ricardofrango.squadify.data.database.AgeGroupStaff
import com.ricardofrango.squadify.data.database.AgeGroups
import com.ricardofrango.squadify.data.database.ClubMembers
import com.ricardofrango.squadify.data.database.Clubs
import com.ricardofrango.squadify.data.database.EmailChangeCodes
import com.ricardofrango.squadify.data.database.FcmTokens
import com.ricardofrango.squadify.data.database.MatchReports
import com.ricardofrango.squadify.data.database.Matches
import com.ricardofrango.squadify.data.database.Opponents
import com.ricardofrango.squadify.data.database.PasswordResetCodes
import com.ricardofrango.squadify.data.database.Players
import com.ricardofrango.squadify.data.database.RefreshTokens
import com.ricardofrango.squadify.data.database.Seasons
import com.ricardofrango.squadify.data.database.Teams
import com.ricardofrango.squadify.data.database.Users
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.domain.usecase.ClubLogos
import com.ricardofrango.squadify.domain.usecase.OpponentLogos
import com.ricardofrango.squadify.domain.usecase.PlayerPhotos
import com.ricardofrango.squadify.domain.usecase.UserAvatars
import com.ricardofrango.squadify.util.toUuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

/** What deleting an account takes with it, for the warning: their own space's contents, if they own one. */
data class OwnSpaceContents(val name: String, val ageGroups: Int, val players: Int, val matches: Int, val staff: Int)

/**
 * Deleting a coach's account (TECHNICAL_DECISIONS §72), in the coach database only. Called inside the use
 * case's transaction. The shared tables' foreign keys mostly refuse a delete (RESTRICT), so the rows go
 * in order, children first.
 */
interface AccountDeletionRepository {
    /** The space [userId] owns and what's in it; null if they own none. */
    suspend fun ownSpace(userId: String): OwnSpaceContents?

    /**
     * Deletes [userId]'s own space with everything in it, takes them out of every other space, makes
     * each other space's owner the author of the match reports they last edited there, and deletes the
     * account with its tokens and codes. Returns the storage keys of the images that went with them
     * (their photo, and their space's logo, players' photos and opponents' logos), for the files to be
     * deleted once the rows are.
     */
    suspend fun delete(userId: String): List<String>
}

class ExposedAccountDeletionRepository : AccountDeletionRepository {

    private fun ownSpaceId(user: Uuid): Uuid? =
        ClubMembers.selectAll().where { (ClubMembers.userId eq user) and (ClubMembers.role eq ClubRole.CLUB_ADMIN) }
            .map { it[ClubMembers.clubId] }.firstOrNull()

    override suspend fun ownSpace(userId: String): OwnSpaceContents? {
        val user = userId.toUuid()
        val spaceId = ownSpaceId(user) ?: return null
        val name = Clubs.selectAll().where { Clubs.id eq spaceId }.single()[Clubs.name]
        return OwnSpaceContents(
            name = name,
            ageGroups = AgeGroups.selectAll().where { AgeGroups.clubId eq spaceId }.count().toInt(),
            players = Players.selectAll().where { Players.clubId eq spaceId }.count().toInt(),
            matches = Matches.selectAll().where { Matches.clubId eq spaceId }.count().toInt(),
            staff = ClubMembers.selectAll().where { (ClubMembers.clubId eq spaceId) and (ClubMembers.userId neq user) }.count().toInt()
        )
    }

    override suspend fun delete(userId: String): List<String> {
        val user = userId.toUuid()
        val images = mutableListOf<String>()
        Users.selectAll().where { Users.id eq user }.forEach { row ->
            row[Users.avatarUrl]?.let { UserAvatars.keyOf(userId, it) }?.let(images::add)
        }
        ownSpaceId(user)?.let { images += deleteSpace(it) }

        // Their reports in other spaces stay, with that space's owner as the author.
        val reports = (MatchReports innerJoin Matches).selectAll().where { MatchReports.updatedBy eq user }
            .map { it[MatchReports.id] to it[Matches.clubId] }
        reports.groupBy({ it.second }, { it.first }).forEach { (spaceId, reportIds) ->
            val owner = ClubMembers.selectAll().where { (ClubMembers.clubId eq spaceId) and (ClubMembers.role eq ClubRole.CLUB_ADMIN) }
                .map { it[ClubMembers.userId] }.single()
            MatchReports.update({ MatchReports.id inList reportIds }) { it[updatedBy] = owner }
        }

        // Out of every other space, and the codes they used (spent already).
        AgeGroupStaff.deleteWhere { AgeGroupStaff.userId eq user }
        ClubMembers.deleteWhere { ClubMembers.userId eq user }
        StaffInvites.deleteWhere { StaffInvites.acceptedBy eq user }

        RefreshTokens.deleteWhere { RefreshTokens.userId eq user }
        FcmTokens.deleteWhere { FcmTokens.userId eq user }
        PasswordResetCodes.deleteWhere { PasswordResetCodes.userId eq user }
        EmailChangeCodes.deleteWhere { EmailChangeCodes.userId eq user }
        Users.deleteWhere { Users.id eq user }
        return images
    }

    /**
     * The space and everything in it. A match takes its call-ups, events, line-ups, stats and report with
     * it. Returns the storage keys of its images.
     */
    private fun deleteSpace(spaceId: Uuid): List<String> {
        val space = spaceId.toString()
        val images = Clubs.selectAll().where { Clubs.id eq spaceId }
            .mapNotNull { row -> row[Clubs.logoUrl]?.let { ClubLogos.keyOf(space, it) } } +
            Players.selectAll().where { Players.clubId eq spaceId }
                .mapNotNull { row -> row[Players.photoUrl]?.let { PlayerPhotos.keyOf(row[Players.id].toString(), it) } } +
            Opponents.selectAll().where { Opponents.clubId eq spaceId }
                .mapNotNull { row -> row[Opponents.logoUrl]?.let { OpponentLogos.keyOf(row[Opponents.id].toString(), it) } }
        val ageGroupIds = AgeGroups.selectAll().where { AgeGroups.clubId eq spaceId }.map { it[AgeGroups.id] }
        Matches.deleteWhere { Matches.clubId eq spaceId }
        Teams.deleteWhere { Teams.clubId eq spaceId }
        AgeGroupStaff.deleteWhere { AgeGroupStaff.ageGroupId inList ageGroupIds }
        AgeGroupPlayers.deleteWhere { AgeGroupPlayers.ageGroupId inList ageGroupIds }
        // Its invite codes go with their age groups (ON DELETE CASCADE).
        AgeGroups.deleteWhere { AgeGroups.clubId eq spaceId }
        Players.deleteWhere { Players.clubId eq spaceId }
        Opponents.deleteWhere { Opponents.clubId eq spaceId }
        Seasons.deleteWhere { Seasons.clubId eq spaceId }
        ClubMembers.deleteWhere { ClubMembers.clubId eq spaceId }
        Clubs.deleteWhere { Clubs.id eq spaceId }
        return images
    }
}
