package com.ricardofrango.squadify.data.repository

import com.ricardofrango.squadify.data.database.Invites
import com.ricardofrango.squadify.domain.repository.InviteRepository
import com.ricardofrango.squadify.exception.DatabaseException
import com.ricardofrango.squadify.model.Invite
import com.ricardofrango.squadify.model.InviteType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.core.inList
import kotlin.uuid.Uuid
import com.ricardofrango.squadify.util.toUuid

class ExposedInviteRepository : InviteRepository {

    override suspend fun createInvite(invite: Invite): Invite =
        Invites.insert {
            it[id] = invite.id.toUuid()
            it[email] = invite.email
            it[clubId] = invite.clubId.toUuid()
            it[type] = invite.type
            it[role] = invite.role
            it[playerId] = invite.playerId?.let(String::toUuid)
            it[relationship] = invite.relationship
            it[createdAt] = invite.createdAt
        }.resultedValues
            ?.singleOrNull()
            ?.let(::toInvite)
            ?: throw DatabaseException("Failed to create invite")

    override suspend fun createInvites(invites: List<Invite>): List<Invite> =
        Invites.batchInsert(invites) { invite ->
            this[Invites.id] = invite.id.toUuid()
            this[Invites.email] = invite.email
            this[Invites.clubId] = invite.clubId.toUuid()
            this[Invites.type] = invite.type
            this[Invites.role] = invite.role
            this[Invites.playerId] = invite.playerId?.let(String::toUuid)
            this[Invites.relationship] = invite.relationship
            this[Invites.createdAt] = invite.createdAt
        }.map(::toInvite)

    override suspend fun deleteInvite(inviteId: String) {
        Invites.deleteWhere { Invites.id eq inviteId.toUuid() }
    }

    override suspend fun deleteInvites(inviteIds: List<String>) {
        Invites.deleteWhere { Invites.id inList inviteIds.map(String::toUuid) }
    }

    override suspend fun findByEmail(email: String): List<Invite> =
        Invites.selectAll().where { Invites.email eq email }
            .map(::toInvite)

    override suspend fun getById(inviteId: String): Invite? =
        Invites.selectAll().where { Invites.id eq inviteId.toUuid() }.map(::toInvite).singleOrNull()

    override suspend fun findClubMemberInvites(clubId: String): List<Invite> =
        Invites.selectAll()
            .where { (Invites.clubId eq clubId.toUuid()) and (Invites.type eq InviteType.CLUB_MEMBER) }
            .map(::toInvite)
            .sortedByDescending { it.createdAt }

    override suspend fun deleteByPlayer(playerId: String) {
        Invites.deleteWhere { Invites.playerId eq playerId.toUuid() }
    }

    private fun toInvite(row: ResultRow): Invite = Invite(
        id = row[Invites.id].toString(),
        email = row[Invites.email],
        clubId = row[Invites.clubId].toString(),
        type = row[Invites.type],
        role = row[Invites.role],
        playerId = row[Invites.playerId]?.toString(),
        relationship = row[Invites.relationship],
        createdAt = row[Invites.createdAt]
    )
}
