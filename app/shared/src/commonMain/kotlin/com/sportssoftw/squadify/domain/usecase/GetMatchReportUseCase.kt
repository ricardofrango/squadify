package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.MatchRepository
import com.sportssoftw.squadify.model.MatchReportDetails

class GetMatchReportUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String): MatchReportDetails = matchRepository.getMatchReport(matchId)
}
