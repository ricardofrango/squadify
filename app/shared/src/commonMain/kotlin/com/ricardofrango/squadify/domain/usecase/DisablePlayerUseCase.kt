package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.PlayerRepository

/** Disables a player in the club, which takes them out of every age group; nothing is deleted. */
class DisablePlayerUseCase(private val playerRepository: PlayerRepository) {
    suspend operator fun invoke(playerId: String) = playerRepository.disablePlayer(playerId)
}
