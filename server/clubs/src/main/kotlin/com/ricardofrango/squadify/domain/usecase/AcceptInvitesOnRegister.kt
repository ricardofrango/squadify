package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.InviteRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.InviteType
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserPlayerRelationship

/**
 * The clubs server's part of signing up: the invites waiting for the new account's email become club
 * memberships and parents' links, and are then deleted. Runs inside RegisterUseCase's transaction.
 */
class AcceptInvitesOnRegister(
    private val playerRepository: PlayerRepository,
    private val inviteRepository: InviteRepository,
    private val clubRepository: ClubRepository
) : NewAccountListener {
    override suspend fun onRegistered(user: User) {
        val invites = inviteRepository.findByEmail(user.email)
        if (invites.isEmpty()) return

        val clubMemberships = mutableListOf<Pair<String, ClubRole>>()
        val parentLinks = mutableListOf<Triple<String, String, UserPlayerRelationship>>()

        invites.forEach { invite ->
            when (invite.type) {
                InviteType.CLUB_MEMBER -> {
                    invite.role
                        ?.let { invite.clubId to it }
                        ?.run(clubMemberships::add)
                }

                InviteType.PARENT_PLAYER -> {
                    val playerId = invite.playerId
                    val relationship = invite.relationship
                    if (playerId != null && relationship != null) {
                        parentLinks.add(Triple(user.id, playerId, relationship))
                    }
                }
            }
        }

        // Several invites can target the same club (e.g. invited as a member, then separately as
        // its director). A user holds one membership per club, so keep the highest role per club.
        clubMemberships
            .groupBy({ it.first }, { it.second })
            .forEach { (clubId, roles) ->
                clubRepository.addMember(clubId, user.id, roles.maxBy { it.level })
            }
        if (parentLinks.isNotEmpty()) {
            playerRepository.createUserPlayers(parentLinks)
        }

        inviteRepository.deleteInvites(invites.map { it.id })
    }
}
