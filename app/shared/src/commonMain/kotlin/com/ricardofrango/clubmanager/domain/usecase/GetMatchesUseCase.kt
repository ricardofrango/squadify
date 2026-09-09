package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.MatchRepository
import com.ricardofrango.clubmanager.model.Match

class GetMatchesUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(clubId: String): List<Match> {
        return matchRepository.getMatches(clubId)
    }
}
