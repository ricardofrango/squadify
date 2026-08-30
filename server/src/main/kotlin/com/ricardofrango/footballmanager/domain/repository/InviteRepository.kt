package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.Invite

interface InviteRepository {
    suspend fun createInvite(invite: Invite): Invite
    suspend fun deleteInvite(inviteId: String)
    suspend fun findByEmail(email: String): List<Invite>
    suspend fun deleteByPlayer(playerId: String)
}
