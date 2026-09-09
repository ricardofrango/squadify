package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.MatchRepository
import com.ricardofrango.clubmanager.model.MatchDetails

class GetMatchDetailsUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String): MatchDetails = matchRepository.getMatchDetails(matchId)
}
