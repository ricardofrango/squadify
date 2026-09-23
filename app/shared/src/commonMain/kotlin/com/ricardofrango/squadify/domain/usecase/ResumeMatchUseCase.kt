package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchRepository

class ResumeMatchUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String) = matchRepository.resumeMatch(matchId)
}
