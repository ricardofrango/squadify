package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.MatchRepository
import com.sportssoftw.squadcall.model.MatchDetails

/** A match's details for its details screen, from the game period's cache; a pull-to-refresh reads the server. */
class ViewMatchDetailsUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String): MatchDetails = matchRepository.getMatchDetailsKept(matchId)
}
