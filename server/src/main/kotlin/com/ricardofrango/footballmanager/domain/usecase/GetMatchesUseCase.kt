package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.Match

class GetMatchesUseCase(
    private val matchRepository: MatchRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(userId: String, clubId: String, teamId: String? = null): List<Match> {
        // 1. Check permissions first (Member of club)
        checkUserPermissionUseCase(userId, clubId, minClubRole = ClubRole.MEMBER, teamId = teamId)

        return if (teamId != null) {
            matchRepository.getMatchesByTeam(teamId)
        } else {
            matchRepository.getMatchesByClub(clubId)
        }
    }
}
