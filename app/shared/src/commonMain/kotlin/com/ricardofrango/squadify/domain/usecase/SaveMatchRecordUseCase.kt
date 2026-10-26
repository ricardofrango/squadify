package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.SaveMatchRecordRequest

class SaveMatchRecordUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, request: SaveMatchRecordRequest): Match =
        matchRepository.saveMatchRecord(matchId, request)
}
