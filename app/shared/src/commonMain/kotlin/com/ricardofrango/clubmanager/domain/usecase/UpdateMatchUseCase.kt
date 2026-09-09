package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.MatchRepository
import com.ricardofrango.clubmanager.model.UpdateMatchRequest

class UpdateMatchUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, request: UpdateMatchRequest) =
        matchRepository.updateMatch(matchId, request)
}
