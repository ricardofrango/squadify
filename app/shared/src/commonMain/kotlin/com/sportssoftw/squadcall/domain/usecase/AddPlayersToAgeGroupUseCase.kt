package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.AgeGroupPlayerRepository
import com.sportssoftw.squadcall.model.AgeGroupPlayer

class AddPlayersToAgeGroupUseCase(
    private val ageGroupPlayerRepository: AgeGroupPlayerRepository
) {
    suspend operator fun invoke(ageGroupId: String, playerIds: List<String>): List<AgeGroupPlayer> {
        return ageGroupPlayerRepository.addPlayers(ageGroupId, playerIds)
    }
}
