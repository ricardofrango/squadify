package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.model.Match

class GetMatchesUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(clubId: String, teamId: String? = null): List<Match> {
        return if (teamId != null) {
            matchRepository.getMatchesByTeam(teamId)
        } else {
            matchRepository.getMatchesByClub(clubId)
        }
    }
}
