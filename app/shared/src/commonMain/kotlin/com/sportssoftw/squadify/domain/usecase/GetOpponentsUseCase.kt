package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.OpponentRepository
import com.sportssoftw.squadify.model.Opponent

class GetOpponentsUseCase(
    private val opponentRepository: OpponentRepository
) {
    suspend operator fun invoke(clubId: String, ageGroupId: String? = null): List<Opponent> {
        return opponentRepository.getOpponents(clubId, ageGroupId)
    }
}
