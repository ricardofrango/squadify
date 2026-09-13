package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupPlayerRepository
import com.ricardofrango.squadify.model.AgeGroupPlayer

class AddPlayersToAgeGroupUseCase(
    private val ageGroupPlayerRepository: AgeGroupPlayerRepository
) {
    suspend operator fun invoke(ageGroupId: String, playerIds: List<String>): List<AgeGroupPlayer> {
        return ageGroupPlayerRepository.addPlayers(ageGroupId, playerIds)
    }
}
