package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.CreatePlayerRequest
import com.ricardofrango.footballmanager.model.Player
import com.ricardofrango.footballmanager.model.Role
import kotlin.uuid.Uuid

class CreatePlayerUseCase(
    private val playerRepository: PlayerRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String, request: CreatePlayerRequest): Boolean {
        val userRole = userRepository.getUserRoleInClub(userId, request.clubId)
        if (userRole != Role.CLUB_ADMIN && userRole != Role.COORDINATOR && userRole != Role.COACH) {
            throw ForbiddenException("Insufficient permissions to create players")
        }

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
