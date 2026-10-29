package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.InviteRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.CreatePlayer
import com.ricardofrango.squadify.model.Invite
import com.ricardofrango.squadify.model.InviteType
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.PlayerParentRequest
import com.ricardofrango.squadify.model.UserPlayerRelationship
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The clubs server's [PlayerContacts]: a player's own email makes them a club member (or invites
 * them), and each parent's email links them to the player (or invites them as a parent).
 */
class ClubPlayerContacts(
    private val playerRepository: PlayerRepository,
    private val userRepository: UserRepository,
    private val inviteRepository: InviteRepository,
    private val clubRepository: ClubRepository
) : PlayerContacts {

    override suspend fun created(clubId: String, players: List<Pair<Player, CreatePlayer>>) {
        val parentLinksToCreate = mutableListOf<Triple<String, String, UserPlayerRelationship>>()
        val parentInvitesToCreate = mutableListOf<Invite>()
        val playerInvitesToCreate = mutableListOf<Invite>()
        val directClubMemberships = mutableListOf<Triple<String, String, ClubRole>>()

        // Collect all unique emails to check in batch
        val allEmails = players.flatMap { (_, p) ->
            p.parents.map { it.email } + listOfNotNull(p.email)
        }.distinct()

        val existingUsersMap = userRepository.findByEmails(allEmails).associateBy { it.email }
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())

        players.forEach { (createdPlayer, playerReq) ->
            // Handle player self-invite
            playerReq.email?.let { pEmail ->
                val user = existingUsersMap[pEmail]
                if (user != null) {
                    directClubMemberships.add(Triple(clubId, user.id, ClubRole.MEMBER))
                } else {
                    playerInvitesToCreate.add(
                        Invite(
                            id = Uuid.random().toString(),
                            email = pEmail,
                            clubId = clubId,
                            type = InviteType.CLUB_MEMBER,
                            role = ClubRole.MEMBER,
                            createdAt = now
                        )
                    )
                }
            }

            // Handle parents
            playerReq.parents.forEach { parentReq ->
                val user = existingUsersMap[parentReq.email]
                if (user != null) {
                    parentLinksToCreate.add(
                        Triple(
                            user.id,
                            createdPlayer.id,
                            parentReq.relationship
                        )
                    )
                } else {
                    parentInvitesToCreate.add(
                        Invite(
                            id = Uuid.random().toString(),
                            email = parentReq.email,
                            clubId = clubId,
                            type = InviteType.PARENT_PLAYER,
                            playerId = createdPlayer.id,
                            relationship = parentReq.relationship,
                            createdAt = now
                        )
                    )
                }
            }
        }

        // Batch operations for associations and invites
        if (directClubMemberships.isNotEmpty()) {
            directClubMemberships.forEach { (cId, uId, role) ->
                clubRepository.addMember(cId, uId, role)
            }
        }
        if (parentLinksToCreate.isNotEmpty()) {
            playerRepository.createUserPlayers(parentLinksToCreate)
        }
        val allInvites = playerInvitesToCreate + parentInvitesToCreate
        if (allInvites.isNotEmpty()) {
            inviteRepository.createInvites(allInvites)
        }
    }

    override suspend fun updated(player: Player, email: String?, parents: List<PlayerParentRequest>?) {
        val playerId = player.id

        // Handle Player's own email invite
        email?.let { playerEmail ->
            val existingUser = userRepository.findByEmail(playerEmail)
            if (existingUser != null) {
                clubRepository.addMember(player.clubId, existingUser.id, ClubRole.MEMBER)
            } else {
                inviteRepository.createInvite(
                    Invite(
                        id = Uuid.random().toString(),
                        email = playerEmail,
                        clubId = player.clubId,
                        type = InviteType.CLUB_MEMBER,
                        role = ClubRole.MEMBER,
                        createdAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
                    )
                )
            }
        }

        if (parents != null) {
            // Total replacement
            playerRepository.deleteUserPlayersByPlayer(playerId)
            inviteRepository.deleteByPlayer(playerId)

            val parentEmails = parents.map { it.email }
            val existingUsers = userRepository.findByEmails(parentEmails).associateBy { it.email }
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())

            val linksToCreate = mutableListOf<Triple<String, String, UserPlayerRelationship>>()
            val invitesToCreate = mutableListOf<Invite>()

            parents.forEach { parentRequest ->
                val user = existingUsers[parentRequest.email]
                if (user != null) {
                    linksToCreate.add(Triple(user.id, playerId, parentRequest.relationship))
                } else {
                    invitesToCreate.add(
                        Invite(
                            id = Uuid.random().toString(),
                            email = parentRequest.email,
                            clubId = player.clubId,
                            type = InviteType.PARENT_PLAYER,
                            playerId = playerId,
                            relationship = parentRequest.relationship,
                            createdAt = now
                        )
                    )
                }
            }

            if (linksToCreate.isNotEmpty()) {
                playerRepository.createUserPlayers(linksToCreate)
            }
            if (invitesToCreate.isNotEmpty()) {
                inviteRepository.createInvites(invitesToCreate)
            }
        }
    }
}
