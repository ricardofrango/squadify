package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.TeamStaffMember

class GetTeamStaffUseCase(
    private val teamRepository: TeamRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(userId: String, teamId: String): List<TeamStaffMember> {
        val team = teamRepository.getTeamById(teamId) ?: throw ValidationException("Team not found")

        // 1. Check permissions first (Member of club)
        checkUserPermissionUseCase(userId, team.clubId, minClubRole = ClubRole.MEMBER, teamId = teamId)

        return teamRepository.getTeamStaff(teamId)
    }
}
