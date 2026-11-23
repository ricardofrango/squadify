package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.MatchRepository

class DeleteMatchEventUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, eventId: String) =
        matchRepository.deleteMatchEvent(matchId, eventId)
}
