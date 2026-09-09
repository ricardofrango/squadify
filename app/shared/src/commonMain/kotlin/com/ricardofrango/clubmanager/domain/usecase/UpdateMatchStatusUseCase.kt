package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.MatchRepository
import com.ricardofrango.clubmanager.model.MatchStatus

class UpdateMatchStatusUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, status: MatchStatus) =
        matchRepository.updateMatchStatus(matchId, status)
}
