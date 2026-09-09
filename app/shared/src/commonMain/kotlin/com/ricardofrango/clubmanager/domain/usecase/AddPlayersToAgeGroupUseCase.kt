package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.AgeGroupPlayerRepository
import com.ricardofrango.clubmanager.model.AgeGroupPlayer

class AddPlayersToAgeGroupUseCase(
    private val ageGroupPlayerRepository: AgeGroupPlayerRepository
) {
    suspend operator fun invoke(ageGroupId: String, playerIds: List<String>): List<AgeGroupPlayer> {
        return ageGroupPlayerRepository.addPlayers(ageGroupId, playerIds)
    }
}
