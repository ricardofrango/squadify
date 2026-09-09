package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.model.Match

class GetMatchesUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(clubId: String): List<Match> {
        return matchRepository.getMatches(clubId)
    }
}
