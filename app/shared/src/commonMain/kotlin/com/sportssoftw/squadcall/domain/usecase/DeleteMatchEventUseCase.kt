package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.MatchRepository

class DeleteMatchEventUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, eventId: String) =
        matchRepository.deleteMatchEvent(matchId, eventId)
}
