package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.MatchRepository

class GetMatchEventsUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String) = matchRepository.getMatchEvents(matchId)
}
