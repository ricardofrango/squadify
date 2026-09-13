package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.model.MatchStatus

class UpdateMatchStatusUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, status: MatchStatus) =
        matchRepository.updateMatchStatus(matchId, status)
}
