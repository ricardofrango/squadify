package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.access.AccessPolicy
import com.sportssoftw.squadcall.domain.access.GameScope
import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.ForbiddenException
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.model.UpdatePlayerRequest

class UpdatePlayerUseCase(
    private val playerRepository: PlayerRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val transactionManager: TransactionManager,
    private val accessPolicy: AccessPolicy,
    private val playerContacts: PlayerContacts
) {
    suspend operator fun invoke(
        userId: String,
        playerId: String,
        request: UpdatePlayerRequest
    ): Boolean = transactionManager.runInTransaction {
        val existingPlayer = playerRepository.getPlayerById(playerId)
            ?: throw NotFoundException("Player not found")

        // Same cross-club spoofing guard as CreatePlayersUseCase -- validate the supplied ageGroupId
        // actually belongs to this player's club before trusting it in the permission check below.
        request.ageGroupId?.let { ageGroupId ->
            val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId)
                ?: throw NotFoundException("Age group not found")
            if (ageGroup.clubId != existingPlayer.clubId) {
                throw ForbiddenException("Age group does not belong to this club")
            }
        }

        accessPolicy.requireEditPlayer(
            userId,
            GameScope(existingPlayer.clubId, ageGroupId = request.ageGroupId),
            "Insufficient permissions to update player"
        )

        val updatedPlayer = existingPlayer.copy(
            firstName = request.firstName ?: existingPlayer.firstName,
            lastName = request.lastName ?: existingPlayer.lastName,
            birthDate = request.birthDate ?: existingPlayer.birthDate,
            shirtNumber = request.shirtNumber ?: existingPlayer.shirtNumber,
            // Only the photo upload sets it (UploadPlayerPhotoUseCase), never a URL sent here.
            photoUrl = existingPlayer.photoUrl,
            position = request.position ?: existingPlayer.position,
            heightCm = request.heightCm ?: existingPlayer.heightCm,
            weightKg = request.weightKg ?: existingPlayer.weightKg,
            emergencyContact = request.emergencyContact ?: existingPlayer.emergencyContact
        )

        val success = playerRepository.updatePlayer(updatedPlayer)

        playerContacts.updated(existingPlayer, request.email, request.parents)

        success
    }
}
