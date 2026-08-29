package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.CreatePlayerRequest
import com.ricardofrango.footballmanager.model.Player
import com.ricardofrango.footballmanager.model.Role
import kotlin.uuid.Uuid

class CreatePlayerUseCase(
    private val playerRepository: PlayerRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(userId: String, request: CreatePlayerRequest): Player {
        checkUserPermissionUseCase(
            userId = userId,
            clubId = request.clubId,
            minRole = Role.COACH,
            errorMessage = "Insufficient permissions to create players"
        )

        if (request.firstName.isBlank() || request.lastName.isBlank()) {
            throw ValidationException("Player name cannot be empty")
        }

        val player = Player(
            id = Uuid.random().toString(),
            clubId = request.clubId,
            firstName = request.firstName,
            lastName = request.lastName,
            birthDate = request.birthDate,
            shirtNumber = request.shirtNumber,
            photoUrl = request.photoUrl,
            active = true
        )

        return playerRepository.createPlayer(player)
    }
}
