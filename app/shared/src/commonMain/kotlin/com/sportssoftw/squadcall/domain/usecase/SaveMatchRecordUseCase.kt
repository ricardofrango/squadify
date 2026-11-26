package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.MatchRepository
import com.sportssoftw.squadcall.model.Match
import com.sportssoftw.squadcall.model.SaveMatchRecordRequest

class SaveMatchRecordUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, request: SaveMatchRecordRequest): Match =
        matchRepository.saveMatchRecord(matchId, request)
}
