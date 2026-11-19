package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.MatchRepository
import com.sportssoftw.squadify.model.Match
import com.sportssoftw.squadify.model.SaveMatchRecordRequest

class SaveMatchRecordUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, request: SaveMatchRecordRequest): Match =
        matchRepository.saveMatchRecord(matchId, request)
}
