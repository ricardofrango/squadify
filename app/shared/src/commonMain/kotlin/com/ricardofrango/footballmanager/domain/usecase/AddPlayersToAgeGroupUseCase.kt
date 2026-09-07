package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.AgeGroupPlayerRepository
import com.ricardofrango.footballmanager.model.AgeGroupPlayer

class AddPlayersToAgeGroupUseCase(
    private val ageGroupPlayerRepository: AgeGroupPlayerRepository
) {
    suspend operator fun invoke(ageGroupId: String, playerIds: List<String>): List<AgeGroupPlayer> {
        return ageGroupPlayerRepository.addPlayers(ageGroupId, playerIds)
    }
}
