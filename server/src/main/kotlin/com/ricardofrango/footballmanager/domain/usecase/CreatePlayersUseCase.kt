package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.CreatePlayersRequest
import com.ricardofrango.footballmanager.model.Player
import com.ricardofrango.footballmanager.model.Role
import kotlin.uuid.Uuid

class CreatePlayersUseCase(
    private val playerRepository: PlayerRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(userId: String, request: CreatePlayersRequest): List<Player> {
        checkUserPermissionUseCase(
            userId = userId,
            clubId = request.clubId,
            minRole = Role.COACH,
            errorMessage = "Insufficient permissions to create players"
        )

        val players = request.players
            .onEach {
                if (it.firstName.isBlank() || it.lastName.isBlank()) {
                    throw ValidationException("Player name cannot be empty")
                }
            }
            .map {
                Player(
                    id = Uuid.random().toString(),
                    clubId = request.clubId,
                    firstName = it.firstName,
                    lastName = it.lastName,
                    birthDate = it.birthDate,
                    shirtNumber = it.shirtNumber,
                    photoUrl = it.photoUrl,
                    active = true
                )
            }

        return playerRepository.createPlayers(players)
    }
}
