package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.OpponentRepository
import com.ricardofrango.footballmanager.model.Opponent

class GetOpponentsUseCase(
    private val opponentRepository: OpponentRepository
) {
    suspend operator fun invoke(clubId: String): List<Opponent> {
        return opponentRepository.getOpponentsByClub(clubId)
    }
}
