package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.OpponentRepository
import com.sportssoftw.squadcall.model.UpdateOpponentRequest

class UpdateOpponentUseCase(
    private val opponentRepository: OpponentRepository
) {
    suspend operator fun invoke(opponentId: String, request: UpdateOpponentRequest) =
        opponentRepository.updateOpponent(opponentId, request)
}
