package com.ricardofrango.clubmanager.domain.repository

import com.ricardofrango.clubmanager.model.Invite

interface InviteRepository {
    suspend fun createInvite(invite: Invite): Invite
    suspend fun createInvites(invites: List<Invite>): List<Invite>
    suspend fun deleteInvite(inviteId: String)
    suspend fun deleteInvites(inviteIds: List<String>)
    suspend fun findByEmail(email: String): List<Invite>
    suspend fun deleteByPlayer(playerId: String)
}
