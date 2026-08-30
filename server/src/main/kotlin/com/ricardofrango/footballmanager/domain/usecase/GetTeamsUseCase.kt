package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.Team

class GetTeamsUseCase(
    private val teamRepository: TeamRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(userId: String, clubId: String): List<Team> {
        // 1. Check permissions first (Member of club)
        checkUserPermissionUseCase(userId, clubId, minClubRole = ClubRole.MEMBER)

        return teamRepository.getTeamsByClub(clubId)
    }
}
