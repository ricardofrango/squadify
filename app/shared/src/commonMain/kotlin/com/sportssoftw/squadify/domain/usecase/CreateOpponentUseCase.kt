package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.OpponentRepository
import com.sportssoftw.squadify.model.CreateOpponentRequest
import com.sportssoftw.squadify.model.Opponent

class CreateOpponentUseCase(
    private val opponentRepository: OpponentRepository
) {
    suspend operator fun invoke(clubId: String, request: CreateOpponentRequest): Opponent {
        return opponentRepository.createOpponent(clubId, request)
    }
}
