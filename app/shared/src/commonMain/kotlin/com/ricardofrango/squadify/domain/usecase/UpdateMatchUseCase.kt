package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.model.UpdateMatchRequest

class UpdateMatchUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, request: UpdateMatchRequest) =
        matchRepository.updateMatch(matchId, request)
}
