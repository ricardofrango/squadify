package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.MatchRepository
import com.sportssoftw.squadify.model.SetMatchLineupRequest

/** Saves the live pitch view's formation and who stands in each position. */
class SetMatchLineupUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, request: SetMatchLineupRequest) =
        matchRepository.setLineup(matchId, request)
}
