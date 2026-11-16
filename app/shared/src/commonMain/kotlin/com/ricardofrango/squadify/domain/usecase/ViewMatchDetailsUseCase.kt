package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.model.MatchDetails

/** A match's details for its details screen, from the game period's cache; a pull-to-refresh reads the server. */
class ViewMatchDetailsUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String): MatchDetails = matchRepository.getMatchDetailsKept(matchId)
}
