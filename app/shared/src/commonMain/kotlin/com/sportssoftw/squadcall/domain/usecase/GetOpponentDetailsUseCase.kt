package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.OpponentRepository
import com.sportssoftw.squadcall.model.OpponentDetails

class GetOpponentDetailsUseCase(
    private val opponentRepository: OpponentRepository
) {
    suspend operator fun invoke(opponentId: String): OpponentDetails = opponentRepository.getOpponentDetails(opponentId)
}
