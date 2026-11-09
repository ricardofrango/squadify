package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.OpponentRepository

/** Deletes an opponent for good (`DELETE /v1/opponents/{id}`), with every game against it. */
class DeleteOpponentUseCase(private val opponentRepository: OpponentRepository) {
    suspend operator fun invoke(opponentId: String) = opponentRepository.deleteOpponent(opponentId)
}
