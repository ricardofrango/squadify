package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.MatchRepository
import com.sportssoftw.squadcall.model.StartMatchRequest

class StartMatchUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, request: StartMatchRequest) =
        matchRepository.startMatch(matchId, request)
}
