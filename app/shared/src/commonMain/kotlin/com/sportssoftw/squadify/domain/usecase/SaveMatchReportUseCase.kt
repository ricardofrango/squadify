package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.MatchRepository
import com.sportssoftw.squadify.model.MatchReport
import com.sportssoftw.squadify.model.SaveMatchReportRequest

class SaveMatchReportUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, request: SaveMatchReportRequest): MatchReport =
        matchRepository.saveMatchReport(matchId, request)
}
