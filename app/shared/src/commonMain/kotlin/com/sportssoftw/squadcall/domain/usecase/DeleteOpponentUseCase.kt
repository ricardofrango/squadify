package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.OpponentRepository

/** Deletes an opponent for good (`DELETE /v1/opponents/{id}`), with every game against it. */
class DeleteOpponentUseCase(private val opponentRepository: OpponentRepository) {
    suspend operator fun invoke(opponentId: String) = opponentRepository.deleteOpponent(opponentId)
}
