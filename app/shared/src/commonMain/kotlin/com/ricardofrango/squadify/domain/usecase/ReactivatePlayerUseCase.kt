package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.PlayerRepository

/** Reactivates a disabled player, so they can be added to an age group again; their old pools aren't restored. */
class ReactivatePlayerUseCase(private val playerRepository: PlayerRepository) {
    suspend operator fun invoke(playerId: String) = playerRepository.reactivatePlayer(playerId)
}
