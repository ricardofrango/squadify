package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.OpponentRepository
import com.ricardofrango.clubmanager.model.Opponent

class GetOpponentsUseCase(
    private val opponentRepository: OpponentRepository
) {
    suspend operator fun invoke(clubId: String): List<Opponent> {
        return opponentRepository.getOpponents(clubId)
    }
}
