package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.OpponentRepository
import com.sportssoftw.squadcall.model.CreateOpponentRequest
import com.sportssoftw.squadcall.model.Opponent

class CreateOpponentUseCase(
    private val opponentRepository: OpponentRepository
) {
    suspend operator fun invoke(clubId: String, request: CreateOpponentRequest): Opponent {
        return opponentRepository.createOpponent(clubId, request)
    }
}
