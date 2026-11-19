package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.MatchRepository
import com.sportssoftw.squadify.model.StartMatchRequest

class StartMatchUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, request: StartMatchRequest) =
        matchRepository.startMatch(matchId, request)
}
