package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.model.Match
import com.ricardofrango.footballmanager.model.Role

class GetMatchesUseCase(
    private val matchRepository: MatchRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(userId: String, clubId: String, teamId: String? = null): List<Match> {
        // 1. Check permissions first (Fail fast)
        checkUserPermissionUseCase(userId, clubId, Role.PLAYER, teamId)

        return if (teamId != null) {
            matchRepository.getMatchesByTeam(teamId)
        } else {
            matchRepository.getMatchesByClub(clubId)
        }
    }
}
