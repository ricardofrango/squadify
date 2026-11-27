package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.AgeGroupPlayerRepository

/** Takes a player out of an age group's pool; they stay in the club. */
class RemovePlayerFromAgeGroupUseCase(private val ageGroupPlayerRepository: AgeGroupPlayerRepository) {
    suspend operator fun invoke(ageGroupId: String, playerId: String) = ageGroupPlayerRepository.removePlayer(ageGroupId, playerId)
}
