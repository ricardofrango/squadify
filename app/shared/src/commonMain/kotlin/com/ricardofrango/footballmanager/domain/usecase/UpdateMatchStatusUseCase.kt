package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.model.MatchStatus

class UpdateMatchStatusUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, status: MatchStatus) =
        matchRepository.updateMatchStatus(matchId, status)
}
