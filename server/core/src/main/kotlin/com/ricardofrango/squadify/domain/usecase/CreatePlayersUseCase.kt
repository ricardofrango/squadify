package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.access.GameScope
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AgeGroupPlayer
import com.ricardofrango.squadify.model.CreatePlayersRequest
import com.ricardofrango.squadify.model.Player
import kotlin.uuid.Uuid

class CreatePlayersUseCase(
    private val playerRepository: PlayerRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val transactionManager: TransactionManager,
    private val accessPolicy: AccessPolicy,
    private val playerContacts: PlayerContacts
) {
    suspend operator fun invoke(userId: String, request: CreatePlayersRequest): List<Player> = transactionManager.runInTransaction {
        // When the caller supplies an age group (the "+ Add new player" shortcut from that age
        // group's own screen), validate it actually belongs to this club before trusting it for the
        // permission check below -- otherwise a coach of Club A's age group could create players in
        // Club B by pairing a mismatched clubId/ageGroupId.
        val ageGroup = request.ageGroupId?.let { ageGroupId ->
            val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId)
                ?: throw NotFoundException("Age group not found")
            if (ageGroup.clubId != request.clubId) {
                throw ForbiddenException("Age group does not belong to this club")
            }
            ageGroup
        }
        if (request.addToAgeGroup && ageGroup == null) {
            throw ValidationException("An age group is needed to add the players to it")
        }

        accessPolicy.requireCreatePlayers(
            userId,
            GameScope(request.clubId, ageGroupId = request.ageGroupId),
            "Only admins, coordinators, or this age group's coaches can create players"
        )
        // Straight into the age group: also who may add players to it, and every one of them eligible
        // (AddPlayersToAgeGroupUseCase's rule), before anything is created.
        if (request.addToAgeGroup && ageGroup != null) {
            accessPolicy.requireManagePool(
                userId,
                GameScope(ageGroup.clubId, ageGroupId = ageGroup.id),
                "Only admins, coordinators, or this age group's coaches can add players"
            )
            request.players.firstOrNull { it.birthDate.year < ageGroup.endYear }?.let {
                throw ValidationException("Player ${it.firstName} ${it.lastName} is too old for this age group")
            }
        }

        // 1. Create players in batch
        val playersToCreate = request.players.map {
            if (it.firstName.isBlank() || it.lastName.isBlank()) {
                throw ValidationException("Player name cannot be empty")
            }
            Player(
                id = Uuid.random().toString(),
                clubId = request.clubId,
                firstName = it.firstName,
                lastName = it.lastName,
                birthDate = it.birthDate,
                shirtNumber = it.shirtNumber,
                // Only the photo upload sets it (UploadPlayerPhotoUseCase), never a URL sent here.
                photoUrl = null,
                active = true,
                position = it.position,
                heightCm = it.heightCm,
                weightKg = it.weightKg,
                emergencyContact = it.emergencyContact
            )
        }

        val createdPlayers = playerRepository.createPlayers(playersToCreate)

        // 2. Their emails, paired with the created players in request order.
        playerContacts.created(request.clubId, createdPlayers.zip(request.players))

        // 3. Into the age group's pool, when asked.
        if (request.addToAgeGroup && ageGroup != null) {
            playerRepository.addPlayersToAgeGroup(
                createdPlayers.map { AgeGroupPlayer(id = Uuid.random().toString(), ageGroupId = ageGroup.id, playerId = it.id) }
            )
        }

        createdPlayers
    }
}
