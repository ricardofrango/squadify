package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.AgeGroupPlayerRepository
import com.ricardofrango.clubmanager.model.Player

class GetEligiblePlayersForAgeGroupUseCase(
    private val ageGroupPlayerRepository: AgeGroupPlayerRepository
) {
    suspend operator fun invoke(ageGroupId: String): List<Player> {
        return ageGroupPlayerRepository.getEligiblePlayers(ageGroupId)
    }
}
