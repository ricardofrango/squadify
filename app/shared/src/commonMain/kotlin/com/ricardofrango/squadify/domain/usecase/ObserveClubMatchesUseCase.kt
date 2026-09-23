package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchRepository

class ObserveClubMatchesUseCase(
    private val matchRepository: MatchRepository
) {
    operator fun invoke(clubId: String) = matchRepository.observeClubMatches(clubId)
}
