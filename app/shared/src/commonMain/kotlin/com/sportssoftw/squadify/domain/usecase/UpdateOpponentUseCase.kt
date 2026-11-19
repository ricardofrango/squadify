package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.OpponentRepository
import com.sportssoftw.squadify.model.UpdateOpponentRequest

class UpdateOpponentUseCase(
    private val opponentRepository: OpponentRepository
) {
    suspend operator fun invoke(opponentId: String, request: UpdateOpponentRequest) =
        opponentRepository.updateOpponent(opponentId, request)
}
