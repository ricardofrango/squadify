package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.model.UpdateMatchRequest

class UpdateMatchUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, request: UpdateMatchRequest) =
        matchRepository.updateMatch(matchId, request)
}
