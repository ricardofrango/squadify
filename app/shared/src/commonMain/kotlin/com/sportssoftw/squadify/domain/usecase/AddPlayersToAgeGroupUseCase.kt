package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.AgeGroupPlayerRepository
import com.sportssoftw.squadify.model.AgeGroupPlayer

class AddPlayersToAgeGroupUseCase(
    private val ageGroupPlayerRepository: AgeGroupPlayerRepository
) {
    suspend operator fun invoke(ageGroupId: String, playerIds: List<String>): List<AgeGroupPlayer> {
        return ageGroupPlayerRepository.addPlayers(ageGroupId, playerIds)
    }
}
