package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.OpponentRepository
import com.sportssoftw.squadify.model.OpponentDetails

class GetOpponentDetailsUseCase(
    private val opponentRepository: OpponentRepository
) {
    suspend operator fun invoke(opponentId: String): OpponentDetails = opponentRepository.getOpponentDetails(opponentId)
}
