package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.MatchRepository

class TakeOverLiveMatchUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String) = matchRepository.takeOverLiveMatch(matchId)
}
