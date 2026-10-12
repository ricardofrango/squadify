package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.model.OpponentDetails

class GetOpponentDetailsUseCase(
    private val opponentRepository: OpponentRepository
) {
    suspend operator fun invoke(opponentId: String): OpponentDetails = opponentRepository.getOpponentDetails(opponentId)
}
