package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.Invite

interface InviteRepository {
    suspend fun createInvite(invite: Invite): Invite
    suspend fun createInvites(invites: List<Invite>): List<Invite>
    suspend fun deleteInvite(inviteId: String)
    suspend fun deleteInvites(inviteIds: List<String>)
    suspend fun findByEmail(email: String): List<Invite>
    suspend fun getById(inviteId: String): Invite?
    // The club's pending CLUB_MEMBER invites, newest first.
    suspend fun findClubMemberInvites(clubId: String): List<Invite>
    suspend fun deleteByPlayer(playerId: String)
}
