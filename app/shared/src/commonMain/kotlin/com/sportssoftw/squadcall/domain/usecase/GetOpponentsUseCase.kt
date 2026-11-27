package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.OpponentRepository
import com.sportssoftw.squadcall.model.Opponent

class GetOpponentsUseCase(
    private val opponentRepository: OpponentRepository
) {
    suspend operator fun invoke(clubId: String, ageGroupId: String? = null): List<Opponent> {
        return opponentRepository.getOpponents(clubId, ageGroupId)
    }
}
