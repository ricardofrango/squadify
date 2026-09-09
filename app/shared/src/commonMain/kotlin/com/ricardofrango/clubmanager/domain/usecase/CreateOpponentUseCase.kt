package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.OpponentRepository
import com.ricardofrango.clubmanager.model.CreateOpponentRequest
import com.ricardofrango.clubmanager.model.Opponent

class CreateOpponentUseCase(
    private val opponentRepository: OpponentRepository
) {
    suspend operator fun invoke(clubId: String, request: CreateOpponentRequest): Opponent {
        return opponentRepository.createOpponent(clubId, request)
    }
}
