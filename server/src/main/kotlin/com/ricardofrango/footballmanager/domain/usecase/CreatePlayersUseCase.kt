package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.CreatePlayersRequest
import com.ricardofrango.footballmanager.model.Player
import com.ricardofrango.footballmanager.model.Role
import kotlin.uuid.Uuid

class CreatePlayersUseCase(
    private val playerRepository: PlayerRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String, request: CreatePlayersRequest): List<Player> {
        val userRole = userRepository.getUserRoleInClub(userId, request.clubId)
        if (userRole != Role.CLUB_ADMIN && userRole != Role.COORDINATOR && userRole != Role.COACH) {
            throw ForbiddenException("Insufficient permissions to create players")
        }

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
