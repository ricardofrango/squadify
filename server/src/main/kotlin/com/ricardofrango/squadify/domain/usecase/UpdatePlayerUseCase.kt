package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.InviteRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

class UpdatePlayerUseCase(
    private val playerRepository: PlayerRepository,
    private val userRepository: UserRepository,
    private val inviteRepository: InviteRepository,
    private val clubRepository: ClubRepository,
    private val transactionManager: TransactionManager,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(
        userId: String,
        playerId: String,
        request: UpdatePlayerRequest
    ): Boolean = transactionManager.runInTransaction {
        val existingPlayer = playerRepository.getPlayerById(playerId)
            ?: throw NotFoundException("Player not found")

        checkUserPermissionUseCase(
            userId = userId,
            clubId = existingPlayer.clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Insufficient permissions to update player"
        )

        val updatedPlayer = existingPlayer.copy(
            firstName = request.firstName ?: existingPlayer.firstName,
            lastName = request.lastName ?: existingPlayer.lastName,
            birthDate = request.birthDate ?: existingPlayer.birthDate,
            shirtNumber = request.shirtNumber ?: existingPlayer.shirtNumber,
            photoUrl = request.photoUrl ?: existingPlayer.photoUrl,
            position = request.position ?: existingPlayer.position,
            heightCm = request.heightCm ?: existingPlayer.heightCm,
            weightKg = request.weightKg ?: existingPlayer.weightKg,
            emergencyContact = request.emergencyContact ?: existingPlayer.emergencyContact
        )

        val success = playerRepository.updatePlayer(updatedPlayer)

        // Handle Player's own email invite
        request.email?.let { playerEmail ->
            val existingUser = userRepository.findByEmail(playerEmail)
            if (existingUser != null) {
                clubRepository.addMember(existingPlayer.clubId, existingUser.id, ClubRole.MEMBER)
            } else {
                inviteRepository.createInvite(
                    Invite(
                        id = Uuid.random().toString(),
                        email = playerEmail,
                        clubId = existingPlayer.clubId,
                        type = InviteType.CLUB_MEMBER,
                        role = ClubRole.MEMBER,
                        createdAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
                    )
                )
            }
        }

        val parents = request.parents
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
                            clubId = existingPlayer.clubId,
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

        success
    }
}
