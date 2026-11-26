package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.MatchRepository
import com.sportssoftw.squadcall.model.SetMatchLineupRequest

/** Saves the live pitch view's formation and who stands in each position. */
class SetMatchLineupUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, request: SetMatchLineupRequest) =
        matchRepository.setLineup(matchId, request)
}
