package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.InviteRepository
import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.NotFoundException
import com.ricardofrango.footballmanager.model.Invite
import com.ricardofrango.footballmanager.model.InviteType
import com.ricardofrango.footballmanager.model.Role
import com.ricardofrango.footballmanager.model.UpdatePlayerRequest
import kotlin.time.Instant
import kotlin.uuid.Uuid

class UpdatePlayerUseCase(
    private val playerRepository: PlayerRepository,
    private val userRepository: UserRepository,
    private val inviteRepository: InviteRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(
        userId: String,
        playerId: String,
        request: UpdatePlayerRequest
    ): Boolean {
        val existingPlayer = playerRepository.getPlayerById(playerId)
            ?: throw NotFoundException("Player not found")

        checkUserPermissionUseCase(
            userId = userId,
            clubId = existingPlayer.clubId,
            minRole = Role.COACH,
            errorMessage = "Insufficient permissions to update player"
        )

        val updatedPlayer = existingPlayer.copy(
            firstName = request.firstName ?: existingPlayer.firstName,
            lastName = request.lastName ?: existingPlayer.lastName,
            birthDate = request.birthDate ?: existingPlayer.birthDate,
            shirtNumber = request.shirtNumber ?: existingPlayer.shirtNumber,
            photoUrl = request.photoUrl ?: existingPlayer.photoUrl
        )

        val success = playerRepository.updatePlayer(updatedPlayer)

        val parents = request.parents
        if (parents != null) {
            // Total replacement
            playerRepository.deleteUserPlayersByPlayer(playerId)
            inviteRepository.deleteByPlayer(playerId)

            val parentEmails = parents.map { it.email }
            val existingUsers = userRepository.findByEmails(parentEmails).associateBy { it.email }

            parents.forEach { parentRequest ->
                val user = existingUsers[parentRequest.email]
                if (user != null) {
                    playerRepository.createUserPlayer(
                        userId = user.id,
                        playerId = playerId,
                        relationship = parentRequest.relationship
                    )
                } else {
                    inviteRepository.createInvite(
                        Invite(
                            id = Uuid.random().toString(),
                            email = parentRequest.email,
                            clubId = existingPlayer.clubId,
                            type = InviteType.PARENT_PLAYER,
                            playerId = playerId,
                            relationship = parentRequest.relationship,
                            createdAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
                        )
                    )
                }
            }
        }

        return success
    }
}
