package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.PlayerRepository

/** Disables a player in the club: they stay in their age groups but leave upcoming call-ups; nothing is deleted. */
class DisablePlayerUseCase(private val playerRepository: PlayerRepository) {
    suspend operator fun invoke(playerId: String) = playerRepository.disablePlayer(playerId)
}
