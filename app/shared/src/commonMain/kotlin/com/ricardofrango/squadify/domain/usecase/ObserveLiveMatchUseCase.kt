package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchRepository

class ObserveLiveMatchUseCase(
    private val matchRepository: MatchRepository
) {
    operator fun invoke(matchId: String) = matchRepository.observeLiveMatch(matchId)
}
