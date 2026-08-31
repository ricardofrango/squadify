package com.ricardofrango.footballmanager.data.repository

import com.ricardofrango.footballmanager.data.database.DatabaseFactory.dbQuery
import com.ricardofrango.footballmanager.data.database.Invites
import com.ricardofrango.footballmanager.domain.repository.InviteRepository
import com.ricardofrango.footballmanager.exception.DatabaseException
import com.ricardofrango.footballmanager.model.Invite
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.core.inList
import kotlin.uuid.Uuid

class ExposedInviteRepository : InviteRepository {
    override suspend fun createInvite(invite: Invite): Invite = dbQuery {
        Invites.insert {
            it[id] = Uuid.parse(invite.id)
            it[email] = invite.email
            it[clubId] = Uuid.parse(invite.clubId)
            it[type] = invite.type
            it[role] = invite.role
            it[playerId] = invite.playerId?.let(Uuid::parse)
            it[relationship] = invite.relationship
            it[createdAt] = invite.createdAt
        }.resultedValues?.singleOrNull()?.let(::toInvite)
            ?: throw DatabaseException("Failed to create invite")
    }

    override suspend fun createInvites(invites: List<Invite>): List<Invite> = dbQuery {
        Invites.batchInsert(invites) { invite ->
            this[Invites.id] = Uuid.parse(invite.id)
            this[Invites.email] = invite.email
            this[Invites.clubId] = Uuid.parse(invite.clubId)
            this[Invites.type] = invite.type
            this[Invites.role] = invite.role
            this[Invites.playerId] = invite.playerId?.let(Uuid::parse)
            this[Invites.relationship] = invite.relationship
            this[Invites.createdAt] = invite.createdAt
        }.map(::toInvite)
    }

    override suspend fun deleteInvite(inviteId: String) {
        dbQuery {
            Invites.deleteWhere { Invites.id eq Uuid.parse(inviteId) }
        }
    }

    override suspend fun deleteInvites(inviteIds: List<String>) {
        dbQuery {
            Invites.deleteWhere { Invites.id inList inviteIds.map(Uuid::parse) }
        }
    }

    override suspend fun findByEmail(email: String): List<Invite> = dbQuery {
        Invites.selectAll().where { Invites.email eq email }
            .map(::toInvite)
    }

    override suspend fun deleteByPlayer(playerId: String) {
        dbQuery {
            Invites.deleteWhere { Invites.playerId eq Uuid.parse(playerId) }
        }
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
