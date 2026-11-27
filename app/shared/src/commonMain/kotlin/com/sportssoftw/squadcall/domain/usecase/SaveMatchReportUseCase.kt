package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.MatchRepository
import com.sportssoftw.squadcall.model.MatchReport
import com.sportssoftw.squadcall.model.SaveMatchReportRequest

class SaveMatchReportUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, request: SaveMatchReportRequest): MatchReport =
        matchRepository.saveMatchReport(matchId, request)
}
