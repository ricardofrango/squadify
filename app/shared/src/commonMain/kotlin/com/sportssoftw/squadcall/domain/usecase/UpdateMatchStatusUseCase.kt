package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.MatchRepository
import com.sportssoftw.squadcall.model.MatchStatus

class UpdateMatchStatusUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, status: MatchStatus) =
        matchRepository.updateMatchStatus(matchId, status)
}
