package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.AgeGroupPlayerRepository
import com.ricardofrango.footballmanager.model.Player

class GetEligiblePlayersForAgeGroupUseCase(
    private val ageGroupPlayerRepository: AgeGroupPlayerRepository
) {
    suspend operator fun invoke(ageGroupId: String): List<Player> {
        return ageGroupPlayerRepository.getEligiblePlayers(ageGroupId)
    }
}
