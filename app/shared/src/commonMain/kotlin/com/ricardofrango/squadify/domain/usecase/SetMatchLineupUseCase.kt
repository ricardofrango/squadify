package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.model.SetMatchLineupRequest

/** Saves the live pitch view's formation and who stands in each position. */
class SetMatchLineupUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, request: SetMatchLineupRequest) =
        matchRepository.setLineup(matchId, request)
}
