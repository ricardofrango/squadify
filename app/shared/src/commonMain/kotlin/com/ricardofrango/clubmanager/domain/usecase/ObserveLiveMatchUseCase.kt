package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.MatchRepository

class ObserveLiveMatchUseCase(
    private val matchRepository: MatchRepository
) {
    operator fun invoke(matchId: String) = matchRepository.observeLiveMatch(matchId)
}
