package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.MatchRepository

class ManageCallUpsUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, playerIds: List<String>) =
        matchRepository.manageCallUps(matchId, playerIds)
}
