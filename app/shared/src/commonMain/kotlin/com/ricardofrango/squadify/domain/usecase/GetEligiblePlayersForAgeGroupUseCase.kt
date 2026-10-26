package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupPlayerRepository
import com.ricardofrango.squadify.model.Paging
import com.ricardofrango.squadify.model.PlayersPage

/** One page of the club players who may join an age group's pool: old enough, active, not in it yet. */
class GetEligiblePlayersForAgeGroupUseCase(
    private val ageGroupPlayerRepository: AgeGroupPlayerRepository
) {
    suspend operator fun invoke(ageGroupId: String, query: String, offset: Int): PlayersPage =
        ageGroupPlayerRepository.getEligiblePlayersPage(ageGroupId, query, offset, Paging.PAGE_SIZE)
}
