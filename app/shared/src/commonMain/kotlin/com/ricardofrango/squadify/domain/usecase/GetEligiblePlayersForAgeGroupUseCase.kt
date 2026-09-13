package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupPlayerRepository
import com.ricardofrango.squadify.model.Player

class GetEligiblePlayersForAgeGroupUseCase(
    private val ageGroupPlayerRepository: AgeGroupPlayerRepository
) {
    suspend operator fun invoke(ageGroupId: String): List<Player> {
        return ageGroupPlayerRepository.getEligiblePlayers(ageGroupId)
    }
}
