package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.MatchRepository
import com.sportssoftw.squadify.model.MatchStatus

class UpdateMatchStatusUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, status: MatchStatus) =
        matchRepository.updateMatchStatus(matchId, status)
}
