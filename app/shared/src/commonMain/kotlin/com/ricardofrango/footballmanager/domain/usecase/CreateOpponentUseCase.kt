package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.OpponentRepository
import com.ricardofrango.footballmanager.model.CreateOpponentRequest
import com.ricardofrango.footballmanager.model.Opponent

class CreateOpponentUseCase(
    private val opponentRepository: OpponentRepository
) {
    suspend operator fun invoke(clubId: String, request: CreateOpponentRequest): Opponent {
        return opponentRepository.createOpponent(clubId, request)
    }
}
