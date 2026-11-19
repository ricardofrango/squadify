package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.MatchRepository
import com.sportssoftw.squadify.model.MatchDetails

class GetMatchDetailsUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String): MatchDetails = matchRepository.getMatchDetails(matchId)
}
