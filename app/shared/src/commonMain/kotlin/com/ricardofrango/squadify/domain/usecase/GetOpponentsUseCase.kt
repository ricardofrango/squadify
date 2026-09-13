package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.model.Opponent

class GetOpponentsUseCase(
    private val opponentRepository: OpponentRepository
) {
    suspend operator fun invoke(clubId: String): List<Opponent> {
        return opponentRepository.getOpponents(clubId)
    }
}
