package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.PlayerRepository

/** Reactivates a disabled player, so they can be called up and added to age groups again. */
class ReactivatePlayerUseCase(private val playerRepository: PlayerRepository) {
    suspend operator fun invoke(playerId: String) = playerRepository.reactivatePlayer(playerId)
}
