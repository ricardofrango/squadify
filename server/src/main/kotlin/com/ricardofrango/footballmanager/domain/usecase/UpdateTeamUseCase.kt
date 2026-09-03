package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.exception.NotFoundException
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.TeamRole
import com.ricardofrango.footballmanager.model.UpdateTeamRequest

class UpdateTeamUseCase(
    private val teamRepository: TeamRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        teamId: String,
        request: UpdateTeamRequest
    ): Boolean = transactionManager.runInTransaction {
        val existingTeam = teamRepository.getTeamById(teamId)
            ?: throw NotFoundException("Team not found")

        checkUserPermissionUseCase(
            userId = userId,
            clubId = existingTeam.clubId,
            minClubRole = ClubRole.COORDINATOR,
            minTeamRole = TeamRole.COACH,
            teamId = teamId,
            errorMessage = "Only admins, coordinators, or this team's coach can update it"
        )

        val updatedTeam = existingTeam.copy(
            name = request.name ?: existingTeam.name,
            code = request.code ?: existingTeam.code,
            active = request.active ?: existingTeam.active
        )

        teamRepository.updateTeam(updatedTeam)
    }
}
