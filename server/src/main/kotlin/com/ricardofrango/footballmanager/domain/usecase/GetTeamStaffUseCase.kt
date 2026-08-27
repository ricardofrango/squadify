package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.model.TeamStaffMember

class GetTeamStaffUseCase(
    private val teamRepository: TeamRepository
) {
    suspend operator fun invoke(teamId: String): List<TeamStaffMember> {
        return teamRepository.getTeamStaff(teamId)
    }
}
