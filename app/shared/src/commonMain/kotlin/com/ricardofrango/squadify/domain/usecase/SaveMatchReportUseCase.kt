package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.model.MatchReport
import com.ricardofrango.squadify.model.SaveMatchReportRequest

class SaveMatchReportUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, request: SaveMatchReportRequest): MatchReport =
        matchRepository.saveMatchReport(matchId, request)
}
