package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.MatchRepository
import com.sportssoftw.squadify.model.UpdateMatchRequest

class UpdateMatchUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, request: UpdateMatchRequest) =
        matchRepository.updateMatch(matchId, request)
}
