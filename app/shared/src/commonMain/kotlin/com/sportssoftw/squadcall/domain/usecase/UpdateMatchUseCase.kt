package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.MatchRepository
import com.sportssoftw.squadcall.model.UpdateMatchRequest

class UpdateMatchUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, request: UpdateMatchRequest) =
        matchRepository.updateMatch(matchId, request)
}
