package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.MatchRepository

class ManageCallUpsUseCase(
    private val matchRepository: MatchRepository
) {
    suspend operator fun invoke(matchId: String, playerIds: List<String>) =
        matchRepository.manageCallUps(matchId, playerIds)
}
