package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchRepository

class TakeOverLiveMatchUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String) = matchRepository.takeOverLiveMatch(matchId)
}
