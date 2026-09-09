package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchRepository

class ManageCallUpsUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, playerIds: List<String>) =
        matchRepository.manageCallUps(matchId, playerIds)
}
