package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.model.MatchDetails

class GetMatchDetailsUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String): MatchDetails = matchRepository.getMatchDetails(matchId)
}
