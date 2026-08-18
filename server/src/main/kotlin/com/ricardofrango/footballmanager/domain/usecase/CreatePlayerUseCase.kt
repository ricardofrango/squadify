package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.Player
import com.ricardofrango.footballmanager.model.Role

class CreatePlayerUseCase(
    private val playerRepository: PlayerRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String, player: Player): Boolean {
        val userRole = userRepository.getUserRoleInClub(userId, player.clubId)
        if (userRole != Role.CLUB_ADMIN && userRole != Role.COORDINATOR && userRole != Role.COACH) {
            throw ForbiddenException("Insufficient permissions to create players")
        }

        if (player.firstName.isBlank() || player.lastName.isBlank()) {
            throw ValidationException("Player name cannot be empty")
        }

        return playerRepository.createPlayer(player)
    }
}
