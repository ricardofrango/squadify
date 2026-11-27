package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.AgeGroupPlayerRepository
import com.sportssoftw.squadcall.model.Paging
import com.sportssoftw.squadcall.model.PlayersPage

/** One page of the club players who may join an age group's pool: old enough, active, not in it yet. */
class GetEligiblePlayersForAgeGroupUseCase(
    private val ageGroupPlayerRepository: AgeGroupPlayerRepository
) {
    suspend operator fun invoke(ageGroupId: String, query: String, offset: Int): PlayersPage =
        ageGroupPlayerRepository.getEligiblePlayersPage(ageGroupId, query, offset, Paging.PAGE_SIZE)
}
